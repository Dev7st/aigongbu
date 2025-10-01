package org.example.paymentservice.purchase.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.paymentservice.admin.repository.PurchasePagingRepository;
import org.example.paymentservice.event.RollbackRequestedMessage;
import org.example.paymentservice.portone.PortOneClient;
import org.example.paymentservice.portone.PortOneVerifyResponse;
import org.example.paymentservice.purchase.entity.Purchase;
import org.example.paymentservice.purchase.repository.PurchaseRepository;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import static org.example.paymentservice.purchase.type.Status.FAILED;

@Service
@RequiredArgsConstructor
@Slf4j
public class FailedPurchaseRecoveryScheduler {

    private final PurchaseRepository purchaseRepository;
    private final PurchasePagingRepository purchasePagingRepository;
    private final PortOneClient portOneClient;
    private final StreamBridge streamBridge;

    @Scheduled(fixedDelay = 60 * 1000)
    public void autoRetryVerification() {
        log.info("⏰ 검증에 실패한 결제 내역을 재검증하는 스케쥴러 시작합니다.");

        purchasePagingRepository.findAllByStatus(FAILED)
                .flatMap(this::retryVerification)
                .subscribe(
                        updated -> log.info("자동 재검증 처리 완료 id={}", updated.id()),
                        error -> log.error("자동 재검증 에러 발생", error)
                );
    }

    private Mono<Purchase> retryVerification(Purchase purchase) {
        return portOneClient.verifyPayment(purchase.impUid())
                .flatMap(rsp -> {
                    PortOneVerifyResponse.VerifyResponse res = rsp.response();

                    // 검증 성공 (금액 일치)
                    if (res != null && res.amount() == purchase.paidAmount()) {
                        log.info("재검증 성공 id={}", purchase.id());
                        Purchase verified = purchase.verify(purchase.impUid());
                        return purchaseRepository.save(verified);
                    }

                    // 포트원 응답 없음 → 이미 취소되었거나 삭제된 imp_uid
                    if (res == null) {
                        String reason = "포트원 (message=" + rsp.message() + ")";
                        log.warn("재검증 실패: {}", reason);
                        Purchase refunded = purchase.refund();
                        return purchaseRepository.save(refunded);
                    }

                    // 금액 불일치 → 위변조 가능성 → 롤백 요청
                    String reason = "금액 불일치 (expected=" + purchase.paidAmount() + ", actual=" + res.amount() + ")";
                    log.warn("재검증 실패: {}, 롤백 요청하겠습니다.", reason);
                    Purchase rollbackRequested = purchase.rollback();
                    publishRollbackEvent(rollbackRequested);
                    return purchaseRepository.save(rollbackRequested);
                })
                .onErrorResume(e -> {
                    log.error("재검증 실패: 포트원 통신 오류 id={}", purchase.id(), e);
                    return saveFailed(purchase, "검증 실패: 포트원 통신 에러");
                });
    }

    private Mono<Purchase> saveFailed(Purchase purchase, String reason) {
        Purchase failed = purchase.fail(reason);
        return purchaseRepository.save(failed);
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
