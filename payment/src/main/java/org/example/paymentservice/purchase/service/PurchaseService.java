package org.example.paymentservice.purchase.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.paymentservice.lecture.LectureClient;
import org.example.paymentservice.portone.PortOneCancelResponse;
import org.example.paymentservice.portone.PortOneClient;
import org.example.paymentservice.purchase.dto.PurchaseCancelRequest;
import org.example.paymentservice.purchase.dto.PurchaseSaveRequest;
import org.example.paymentservice.purchase.dto.PurchaseSummaryResponse;
import org.example.paymentservice.purchase.entity.CancelFailure;
import org.example.paymentservice.purchase.entity.Purchase;
import org.example.paymentservice.purchase.repository.CancelFailureRepository;
import org.example.paymentservice.purchase.repository.PurchaseRepository;
import org.example.paymentservice.saga.PurchaseSagaCoordinator;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.example.paymentservice.purchase.type.Status.COMPLETED;
import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final CancelFailureRepository cancelFailureRepository;
    private final PortOneClient portOneClient;
    private final LectureClient lectureClient;
    private final PurchaseSagaCoordinator sagaCoordinator;

    public Mono<Purchase> savePurchase(PurchaseSaveRequest request, Long userId) {
        return lectureClient.reserveDiscount(request.getProductId())
                .map(response -> {
                    int finalPaidAmount = response.applied()
                            ? applyDiscount(request.getProductPrice(), response.discountRate())
                            : request.getProductPrice();

                    PurchaseSaveRequest discountedRequest = new PurchaseSaveRequest();
                            discountedRequest.setProductId(request.getProductId());
                            discountedRequest.setMerchantUid(request.getMerchantUid());
                            discountedRequest.setImpUid(request.getImpUid());
                            discountedRequest.setProductPrice(request.getProductPrice());
                            discountedRequest.setPaidAmount(finalPaidAmount);
                            discountedRequest.setPaymentMethod(request.getPaymentMethod());

                    return discountedRequest;
                })
                .flatMap(discountedRequest -> sagaCoordinator.execute(discountedRequest, userId));
    }

    public Mono<Purchase> cancelPayment(PurchaseCancelRequest request, Long userId) {
        return purchaseRepository.findByMerchantUid(request.getMerchantUid())
                .switchIfEmpty(Mono.error(new ResponseStatusException(NOT_FOUND, "결제 내역을 찾을 수 없습니다.")))
                .flatMap(purchase -> {
                    if (!purchase.userId().equals(userId)) {
                        return Mono.error(new ResponseStatusException(FORBIDDEN, "해당 결제에 대한 권한이 없습니다."));
                    }

                    if (purchase.status() != COMPLETED) {
                        return Mono.error(new ResponseStatusException(BAD_REQUEST, "검증이 완료된 결제 내역만 취소할 수 있습니다."));
                    }

                    return portOneClient.cancelPayment(purchase.impUid(), purchase.paidAmount())
                            .flatMap(rsp -> {
                                PortOneCancelResponse.CancelResponse res = rsp.response();

                                if (res != null && "cancelled".equals(res.status())) {
                                    Purchase canceled = purchase.cancel();
                                    return purchaseRepository.save(canceled)
                                            .onErrorResume(e -> {
                                                log.error("환불은 완료되었으나, DB 업데이트에 실패하였습니다.", e);
                                                return saveCancelFailure(purchase, e.getMessage())
                                                        .then(Mono.error(new ResponseStatusException(
                                                                BAD_REQUEST,
                                                                "환불은 완료되었으나, DB 업데이트에 실패"
                                                        )));
                                            });
                                } else {
                                    log.warn("취소 실패: 상태값={}, 메시지={}", res != null ? res.status() : "null", rsp.message());
                                    return Mono.error(new ResponseStatusException(BAD_REQUEST, "결제 취소에 실패하였습니다."));
                                }
                            });
                });
    }

    public Flux<PurchaseSummaryResponse> findByUserId(Long userId) {
        return purchaseRepository.findAllByUserId(userId)
                .filter(p -> p.status() == COMPLETED)
                .flatMap(purchase ->
                        lectureClient.getLectureInfo(purchase.productId())
                                .map(lecture -> PurchaseSummaryResponse.of(purchase, lecture))
                );
    }

    public int applyDiscount(int productPrice, BigDecimal discountRate) {
        if (discountRate == null || discountRate.compareTo(BigDecimal.ZERO) <= 0) {
            return productPrice;
        }

        BigDecimal multiplier = BigDecimal.ONE.subtract(discountRate.divide(BigDecimal.valueOf(100)));
        return BigDecimal.valueOf(productPrice)
                .multiply(multiplier)
                .setScale(0, RoundingMode.FLOOR)
                .intValue();
    }

    private Mono<Void> saveCancelFailure(Purchase purchase, String reason) {
        CancelFailure failure = new CancelFailure(
                null,
                purchase.id(),
                purchase.impUid(),
                purchase.paidAmount(),
                reason,
                null
        );

        return cancelFailureRepository.save(failure).then();
    }
}
