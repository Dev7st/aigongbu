package org.example.paymentservice.webhook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.paymentservice.event.RollbackRequestedMessage;
import org.example.paymentservice.lecture.LectureClient;
import org.example.paymentservice.portone.PortOneClient;
import org.example.paymentservice.purchase.entity.Purchase;
import org.example.paymentservice.purchase.repository.PurchaseRepository;
import org.example.paymentservice.purchase.type.Status;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentRecoveryScheduler {

    private final PortOneClient portOneClient;
    private final PurchaseRepository purchaseRepository;
    private final LectureClient lectureClient;
    private final StreamBridge streamBridge;

    @Scheduled(fixedDelay = 60000) // 1분마다 실행
    public void recoverMissingPayments() {
        log.info("⏰ 웹훅 스케쥴러 시작합니다.");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime fiveMinutesAgo = now.minusMinutes(5);

        portOneClient.getPaidPaymentsWithinPeriod(fiveMinutesAgo, now)
                .flatMapMany(Flux::fromIterable)
                .flatMap(payment -> purchaseRepository.existsByImpUid(payment.getImpUid())
                        .filter(exists -> !exists)
                        .flatMap(unused -> {
                            Long userId = Long.valueOf(payment.getCustomData().get("user_id").toString());
                            Long productId = Long.valueOf(payment.getCustomData().get("product_id").toString());
                            Integer paidAmount = payment.getAmount();

                            return lectureClient.getLectureInfo(productId)
                                    .flatMap(lecture -> {
                                        Integer productPrice = lecture.price();
                                        Status status = paidAmount.equals(productPrice)
                                                ? Status.COMPLETED
                                                : Status.ROLLBACK_REQUESTED;

                                        Purchase purchase = Purchase.builder()
                                                .userId(userId)
                                                .productId(productId)
                                                .merchantUid(payment.getMerchantUid())
                                                .impUid(payment.getImpUid())
                                                .productPrice(productPrice)
                                                .paidAmount(paidAmount)
                                                .paymentMethod(payment.getPaymentMethod())
                                                .status(status)
                                                .createdAt(Instant.now())
                                                .isVerified(status == Status.COMPLETED)
                                                .reason(status == Status.ROLLBACK_REQUESTED ? "결제 금액 불일치" : null)
                                                .build();

                                        return purchaseRepository.save(purchase)
                                                .doOnSuccess(saved -> {
                                                    log.info("결제 저장 완료: {}", saved.impUid());
                                                    if (status == Status.ROLLBACK_REQUESTED) {
                                                        streamBridge.send("rollback-out-0", new RollbackRequestedMessage(
                                                                saved.id(),
                                                                saved.impUid(),
                                                                saved.paidAmount(),
                                                                saved.productId()
                                                        ));
                                                        log.warn("ROLLBACK 이벤트 발행: {}", saved.impUid());
                                                    }
                                                });
                                    });
                        })
                )
                .onErrorContinue((e, obj) -> log.error("복구 실패: {}", obj, e))
                .subscribe();
    }
}
