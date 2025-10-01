package org.example.paymentservice.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.paymentservice.admin.dto.CancelFailureResponse;
import org.example.paymentservice.admin.dto.PurchaseFailureResponse;
import org.example.paymentservice.admin.dto.RollBackFailureResponse;
import org.example.paymentservice.admin.repository.CancelPagingRepository;
import org.example.paymentservice.admin.repository.PurchasePagingRepository;
import org.example.paymentservice.admin.repository.RollBackPagingRepository;
import org.example.paymentservice.event.RollbackRequestedMessage;
import org.example.paymentservice.portone.PortOneCancelResponse;
import org.example.paymentservice.portone.PortOneClient;
import org.example.paymentservice.purchase.entity.Purchase;
import org.example.paymentservice.purchase.repository.CancelFailureRepository;
import org.example.paymentservice.purchase.repository.PurchaseRepository;
import org.example.paymentservice.purchase.repository.RollBackFailureRepository;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.example.paymentservice.purchase.type.Status.FAILED;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final RollBackFailureRepository failureRepository;
    private final CancelFailureRepository cancelFailureRepository;
    private final RollBackPagingRepository rPagingRepository;
    private final PurchasePagingRepository pPagingRepository;
    private final CancelPagingRepository cPagingRepository;
    private final PurchaseRepository purchaseRepository;
    private final PortOneClient portOneClient;
    private final StreamBridge streamBridge;

    // Spring Data R2DBC 페이징 표준 구조
    public Flux<RollBackFailureResponse> getFailedRefund(Pageable pageable) {
        int page = pageable.getPageNumber();
        int size = pageable.getPageSize();

        return rPagingRepository.findAll()
                .skip((long) page * size)
                .take(size)
                .map(RollBackFailureResponse::getFailure);
    }

    public Flux<PurchaseFailureResponse> getFailedPurchases(Pageable pageable) {
        int page = pageable.getPageNumber();
        int size = pageable.getPageSize();

        return pPagingRepository.findAllByStatus(FAILED)
                .skip((long) page * size)
                .take(size)
                .map(PurchaseFailureResponse::getFailure);
    }

    public Flux<CancelFailureResponse> getFailedCancels(Pageable pageable) {
        int page = pageable.getPageNumber();
        int size = pageable.getPageSize();

        return cPagingRepository.findAll()
                .skip((long) page * size)
                .take(size)
                .map(CancelFailureResponse::getFailure);
    }

    public Mono<String> forceRefund(Long id) {
        return failureRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalStateException("롤백 실패 내역을 찾을 수 없습니다.")))
                .flatMap(failure ->
                        purchaseRepository.findById(failure.purchaseId())
                                .switchIfEmpty(Mono.error(new IllegalStateException("결제 내역을 찾을 수 없습니다.")))
                                .flatMap(purchase -> {
                                    switch (purchase.status()) {
                                        case CANCELED, REFUNDED -> {
                                            String reason = String.format("이미 환불 처리된 결제입니다. (id=%d, status=%s)", purchase.id(), purchase.status());
                                            log.warn(reason);
                                            return Mono.error(new IllegalStateException(reason));
                                        }
                                        default -> {
                                            return portOneClient.cancelPayment(failure.impUid(), failure.amount())
                                                    .flatMap(rsp -> {
                                                        PortOneCancelResponse.CancelResponse res = rsp.response();

                                                        if (res == null) {
                                                            String reason = String.format("강제 환불 실패: %s", rsp.message());
                                                            log.error(reason);
                                                            return Mono.error(new RuntimeException(reason));
                                                        }

                                                        Purchase refund = purchase.refund();
                                                        return purchaseRepository.save(refund)
                                                                .then(failureRepository.deleteById(id))
                                                                .thenReturn("강제 환불 성공: id=" + purchase.id());
                                                    });
                                        }
                                    }
                                })
                );
    }

    public Mono<String> retryVerification(Long id) {
        return purchaseRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalStateException("결제 내역을 찾을 수 없습니다.")))
                .filter(purchase -> purchase.status() == FAILED)
                .switchIfEmpty(Mono.error(new IllegalStateException("검증에 실패한 결제만 재검증할 수 있습니다.")))
                .flatMap(purchase ->
                        portOneClient.verifyPayment(purchase.impUid())
                                .flatMap(rsp -> {
                                    if (rsp.response() == null) {
                                        // 이미 취소 처리되어 포트원 서버에 imp_uid가 삭제된 검증할 수 없는 케이스
                                        String reason = "검증 실패: 포트원 응답 (message=" + rsp.message() + ")";
                                        log.error(reason);

                                        Purchase refunded = purchase.refund();
                                        return purchaseRepository.save(refunded)
                                                .thenReturn("이미 환불된 결제로 간주되어 REFUNDED 처리하였습니다.");
                                    }

                                    boolean isValid = rsp.response().amount() == purchase.paidAmount();
                                    if (isValid) {
                                        Purchase verified = purchase.verify(purchase.impUid());
                                        return purchaseRepository.save(verified)
                                                .thenReturn("검증 성공: id=" + purchase.id());
                                    } else {
                                        String reason = "검증 실패: 결제 금액 불일치 (expected=" + purchase.paidAmount() + ", actual=" + rsp.response().amount() + ")";
                                        log.error(reason);
                                        Purchase rollbackRequested = purchase.rollback();

                                        publishRollbackEvent(rollbackRequested);

                                        return purchaseRepository.save(rollbackRequested)
                                                .thenReturn(reason);
                                    }
                                })
                                .onErrorResume(e -> {
                                    String reason = "검증 실패: 포트원 통신 에러=" + e.getMessage();
                                    log.error(reason);
                                    return saveFailed(purchase, reason)
                                            .thenReturn(reason);
                                })
                );
    }

    public Mono<String> forceCancel(Long purchaseId) {
        return purchaseRepository.findById(purchaseId)
                .switchIfEmpty(Mono.error(new IllegalStateException("결제 내역을 찾을 수 없습니다.")))
                .flatMap(purchase -> {
                    switch (purchase.status()) {
                        case CANCELED, REFUNDED -> {
                            String reason = String.format("이미 취소 완료된 결제입니다. (id=%d, status=%s)", purchase.id(), purchase.status());
                            log.warn(reason);
                            return Mono.just(reason);
                        }
                        default -> {
                            Purchase canceled = purchase.cancel();
                            return purchaseRepository.save(canceled)
                                    .then(cancelFailureRepository.findByPurchaseId(purchase.id()))
                                    .flatMap(failure -> cancelFailureRepository.deleteById(failure.id()))
                                    .thenReturn("취소 처리 업데이트 성공: id=" + purchase.id());
                        }
                    }
                });
    }

    private Mono<Void> saveFailed(Purchase purchase, String reason) {
        Purchase failed = new Purchase(
                purchase.id(),
                purchase.userId(),
                purchase.productId(),
                purchase.merchantUid(),
                purchase.impUid(),
                purchase.productPrice(),
                purchase.paidAmount(),
                purchase.paymentMethod(),
                FAILED,
                purchase.createdAt(),
                false,
                reason
        );

        return purchaseRepository.save(failed).then();
    }

    private void publishRollbackEvent(Purchase purchase) {
        RollbackRequestedMessage message = new RollbackRequestedMessage(
                purchase.id(),
                purchase.impUid(),
                purchase.paidAmount(),
                purchase.productId()
        );
        streamBridge.send("rollback-out-0", message);
    }
}
