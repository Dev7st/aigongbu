package org.example.paymentservice.webhook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.paymentservice.event.RollbackRequestedMessage;
import org.example.paymentservice.lecture.LectureClient;
import org.example.paymentservice.portone.PortOneClient;
import org.example.paymentservice.purchase.entity.Purchase;
import org.example.paymentservice.purchase.repository.PurchaseRepository;
import org.example.paymentservice.purchase.type.Status;
import org.example.paymentservice.webhook.dto.PortOneWebhookRequest;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/portone")
@Slf4j
public class PortOneWebhookController {

    private final PortOneClient portOneClient;
    private final PurchaseRepository purchaseRepository;
    private final LectureClient lectureClient;
    private final StreamBridge streamBridge;

    @PostMapping("/webhook")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Void> receiveWebhook(@RequestBody PortOneWebhookRequest request) {
        log.info("포트원에서 웹훅으로 결제건을 받아옵니다.: {}", request);
        String impUid = request.getImpUid();

        return purchaseRepository.existsByImpUid(impUid)
                .flatMap(exists -> {
                    if (exists) {
                        log.info("이미 처리된 imp_uid: {}", impUid);
                        return Mono.empty();
                    }

                    return portOneClient.getPaymentByImpUid(impUid)
                            .flatMap(payment -> {
                                try {
                                    Map<String, Object> customData = payment.getCustomData();
                                    if (payment.getAmount() == null || customData == null ||
                                            !customData.containsKey("user_id") || !customData.containsKey("product_id")) {
                                        log.warn("유효성이 없는 웹훅: {}", payment);
                                        return Mono.empty();
                                    }

                                    Long userId = Long.valueOf(customData.get("user_id").toString());
                                    Long productId = Long.valueOf(customData.get("product_id").toString());
                                    Integer paidAmount = payment.getAmount();

                                    return lectureClient.getLectureInfo(productId)
                                            .flatMap(lecture -> {
                                                Integer productPrice = lecture.price();
                                                if (productPrice == null || paidAmount <= 0) {
                                                    log.warn("강의 가격 정보 없음 또는 비정상 결제 금액");
                                                    return Mono.empty();
                                                }

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
                                                        .flatMap(saved -> {
                                                            if (status == Status.ROLLBACK_REQUESTED) {
                                                                RollbackRequestedMessage message = new RollbackRequestedMessage(
                                                                        saved.id(),
                                                                        saved.impUid(),
                                                                        saved.paidAmount(),
                                                                        saved.productId()
                                                                );
                                                                streamBridge.send("rollback-out-0", message);
                                                                log.warn("ROLLBACK 이벤트 발행: {}", message);
                                                            }
                                                            return Mono.empty();
                                                        });
                                            });

                                } catch (Exception e) {
                                    log.error("Webhook 처리 중 예외 발생", e);
                                    return Mono.empty();
                                }
                            });
                });
    }
}
