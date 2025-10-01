package org.example.paymentservice.saga;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.paymentservice.event.*;
import org.example.paymentservice.purchase.entity.Purchase;
import org.example.paymentservice.purchase.entity.RollBackFailure;
import org.example.paymentservice.purchase.repository.RollBackFailureRepository;
import org.example.paymentservice.purchase.repository.PurchaseRepository;
import org.example.paymentservice.purchase.dto.PurchaseSaveRequest;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.example.paymentservice.purchase.type.Status.PENDING;

@Component
@RequiredArgsConstructor
@Slf4j
public class PurchaseSagaCoordinator {

    private final PurchaseRepository purchaseRepository;
    private final RollBackFailureRepository failureRepository;
    private final StreamBridge streamBridge;

    public Mono<Purchase> execute(PurchaseSaveRequest request, Long userId) {
        Purchase pending = Purchase.builder()
                .userId(userId)
                .productId(request.getProductId())
                .merchantUid(request.getMerchantUid())
                .impUid(request.getImpUid())
                .productPrice(request.getProductPrice())
                .paidAmount(request.getPaidAmount())
                .paymentMethod(request.getPaymentMethod())
                .status(PENDING)
                .createdAt(Instant.now())
                .isVerified(false)
                .build();

        return purchaseRepository.save(pending)
                .doOnNext(this::publishVerifyEvent);
    }

    public Flux<Purchase> consumerVerifiedEvent(Flux<VerifiedMessage> messageFlux) {
        return messageFlux.flatMap(message -> {
            Long purchaseId = message.purchaseId();

            return purchaseRepository.findById(purchaseId)
                    .filter(p -> p.status() == PENDING)
                    .flatMap(purchase -> {
                        if (message.isValid()) {
                            log.info("검증 성공: purchaseId={}", purchaseId);
                            Purchase verified = purchase.verify(purchase.impUid());
                            return purchaseRepository.save(verified);
                        }

                        String reason = message.reason();
                        if (reason != null) {
                            if (reason.contains("금액 불일치")) {
                                log.warn("검증 실패: id={}, 원인={}", purchaseId, reason);
                                return rollback(purchase, new RuntimeException(reason));
                            } else {
                                log.error("검증 실패: 포트원 id={}, 원인={}", purchaseId, reason);
                                Purchase failed = purchase.fail(reason);
                                return purchaseRepository.save(failed);
                            }
                        } else {
                            log.error("검증 실패: 서버 문제 id={}", purchaseId);
                            Purchase failed = purchase.fail("검증 실패: 서버 문제");
                            return purchaseRepository.save(failed);
                        }
                    })
                    .switchIfEmpty(Mono.fromRunnable(() ->
                            log.warn("이미 처리된 결제입니다. id={}", purchaseId)))
                    .onErrorResume(e -> {
                        log.error("검증 이벤트 처리 실패 id={}, 원인={}", purchaseId, e.getMessage());
                        return Mono.empty();
                    });
        });
    }

    public Flux<Purchase> consumerRollBackedEvent(Flux<RollBackedMessage> messageFlux) {
        return messageFlux.flatMap(message ->
                purchaseRepository.findById(message.purchaseId())
                        .flatMap(purchase -> {
                            if (message.isRollBacked()) {
                                Purchase refund = purchase.refund(); // 결제 검증이 실패했기 때문에 롤백으로 취소처리 됨 = 결제 환불
                                return purchaseRepository.save(refund);
                            } else {
                                return saveRollBackFailureLog(purchase, message.reason())
                                        .then(Mono.empty()); // 실패 로그만 저장하고 별도로 반환할 Purchase 객체 없이 흐름 끝
                            }
                        })
        );
    }

    private Mono<Purchase> rollback(Purchase purchase, Throwable cause) {
        if (purchase.status() != PENDING) {
            log.warn("롤백 불가 상태입니다. id={}, status={}", purchase.id(), purchase.status());
            Purchase failed = purchase.fail("롤백 불가: 이미 처리된 결제(status=\" + purchase.status() + \")");
            return purchaseRepository.save(failed);
        }

        log.warn("롤백 트랜잭션 실행합니다. 원인={}", cause.getMessage());

        Purchase rollbacked = purchase.rollback();
        publishRollBackEvent(rollbacked);
        return purchaseRepository.save(rollbacked);
    }

    private void publishVerifyEvent(Purchase purchase) {
        VerifyRequestedMessage message = new VerifyRequestedMessage(
                purchase.id(), purchase.impUid(), purchase.paidAmount()
        );

        streamBridge.send("verify-out-0", message);
    }

    private void publishRollBackEvent(Purchase purchase) {
        RollbackRequestedMessage message = new RollbackRequestedMessage(
                purchase.id(), purchase.impUid(), purchase.paidAmount(), purchase.productId()
        );

        streamBridge.send("rollback-out-0", message);
    }

    private Mono<Void> saveRollBackFailureLog(Purchase purchase, String reason) {
        RollBackFailure failureLog = RollBackFailure.builder()
                .purchaseId(purchase.id())
                .impUid(purchase.impUid())
                .amount(purchase.paidAmount())
                .reason(reason)
                .createdAt(Instant.now())
                .build();

        return failureRepository.save(failureLog).then();
    }
}
