package org.example.paymentservice.purchase.service;

import org.example.paymentservice.lecture.LectureClient;
import org.example.paymentservice.lecture.LectureDiscountResponse;
import org.example.paymentservice.portone.PortOneCancelResponse;
import org.example.paymentservice.portone.PortOneClient;
import org.example.paymentservice.purchase.dto.PurchaseCancelRequest;
import org.example.paymentservice.purchase.dto.PurchaseSaveRequest;
import org.example.paymentservice.purchase.entity.Purchase;
import org.example.paymentservice.purchase.repository.CancelFailureRepository;
import org.example.paymentservice.purchase.repository.PurchaseRepository;
import org.example.paymentservice.purchase.type.Status;
import org.example.paymentservice.saga.PurchaseSagaCoordinator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PurchaseServiceTest {

    @Mock private PurchaseRepository purchaseRepository;
    @Mock private CancelFailureRepository cancelFailureRepository;
    @Mock private PortOneClient portOneClient;
    @Mock private LectureClient lectureClient;
    @Mock private PurchaseSagaCoordinator sagaCoordinator;

    @InjectMocks
    private PurchaseService purchaseService;

    // ✅ [1] 결제 저장 단위 테스트
    @Test
    void 결제요청시_Purchase가_PENDING상태로_저장된다() {
        Long userId = 1L;
        PurchaseSaveRequest request = new PurchaseSaveRequest();
        request.setProductId(10L);
        request.setMerchantUid("merchant-123");
        request.setImpUid("imp-001");
        request.setProductPrice(10000);
        request.setPaidAmount(10000);
        request.setPaymentMethod("card");

        LectureDiscountResponse discountResponse = new LectureDiscountResponse(false, BigDecimal.ZERO);
        given(lectureClient.reserveDiscount(anyLong())).willReturn(Mono.just(discountResponse));

        Purchase expectedPurchase = Purchase.builder()
                .id(1L)
                .userId(userId)
                .productId(request.getProductId())
                .merchantUid(request.getMerchantUid())
                .impUid(request.getImpUid())
                .productPrice(request.getProductPrice())
                .paidAmount(request.getPaidAmount())
                .paymentMethod(request.getPaymentMethod())
                .status(Status.PENDING)
                .createdAt(Instant.now())
                .isVerified(false)
                .build();

        given(sagaCoordinator.execute(any(), any())).willReturn(Mono.just(expectedPurchase));

        Purchase result = purchaseService.savePurchase(request, userId).block();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(Status.PENDING);
        assertThat(result.merchantUid()).isEqualTo("merchant-123");
    }

    // ✅ [2] 결제 취소 관련 단위 테스트들 (기존 전체 그대로 유지)

    @Test
    void 결제취소_정상적으로_처리된다() {
        // given
        String merchantUid = "merchant-123";
        Long userId = 1L;
        int paidAmount = 10000;

        Purchase original = Purchase.builder()
                .id(1L)
                .userId(userId)
                .merchantUid(merchantUid)
                .impUid("imp-001")
                .productId(10L)
                .productPrice(paidAmount)
                .paidAmount(paidAmount)
                .paymentMethod("card")
                .status(Status.COMPLETED)
                .createdAt(Instant.now())
                .isVerified(true)
                .build();

        PortOneCancelResponse response = new PortOneCancelResponse(
                0, "success",
                new PortOneCancelResponse.CancelResponse(
                        "imp-001", merchantUid, paidAmount, "cancelled"
                )
        );

        Purchase canceled = original.cancel();

        given(purchaseRepository.findByMerchantUid(merchantUid)).willReturn(Mono.just(original));
        given(portOneClient.cancelPayment("imp-001", paidAmount)).willReturn(Mono.just(response));
        given(purchaseRepository.save(any(Purchase.class))).willReturn(Mono.just(canceled));

        PurchaseCancelRequest request = new PurchaseCancelRequest();
        request.setMerchantUid(merchantUid);

        // when
        Purchase result = purchaseService.cancelPayment(request, userId).block();

        // then
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(Status.CANCELED);
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.merchantUid()).isEqualTo(merchantUid);
    }

    @Test
    void 결제내역이없으면_NOT_FOUND_예외가_발생한다() {
        // given
        String merchantUid = "not-exist";
        PurchaseCancelRequest request = new PurchaseCancelRequest();
        request.setMerchantUid(merchantUid);

        given(purchaseRepository.findByMerchantUid(merchantUid)).willReturn(Mono.empty());

        // when
        Throwable ex = catchThrowable(() -> purchaseService.cancelPayment(request, 1L).block());

        // then
        assertThat(ex).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void 사용자ID가_일치하지않으면_FORBIDDEN_예외가_발생한다() {
        // given
        Long userId = 1L;
        Long otherUserId = 2L;
        String merchantUid = "merchant-123";

        Purchase purchase = Purchase.builder()
                .id(1L)
                .userId(userId)
                .merchantUid(merchantUid)
                .impUid("imp-001")
                .productId(10L)
                .productPrice(10000)
                .paidAmount(10000)
                .paymentMethod("card")
                .status(Status.COMPLETED)
                .createdAt(Instant.now())
                .isVerified(true)
                .build();

        given(purchaseRepository.findByMerchantUid(merchantUid)).willReturn(Mono.just(purchase));

        PurchaseCancelRequest request = new PurchaseCancelRequest();
        request.setMerchantUid(merchantUid);

        // when
        Throwable ex = catchThrowable(() -> purchaseService.cancelPayment(request, otherUserId).block());

        // then
        assertThat(ex).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void 상태가_COMPLETED가_아니면_BAD_REQUEST_예외가_발생한다() {
        // given
        String merchantUid = "merchant-123";
        Long userId = 1L;

        Purchase purchase = Purchase.builder()
                .id(1L)
                .userId(userId)
                .merchantUid(merchantUid)
                .impUid("imp-001")
                .productId(10L)
                .productPrice(10000)
                .paidAmount(10000)
                .paymentMethod("card")
                .status(Status.PENDING) // 핵심
                .createdAt(Instant.now())
                .isVerified(true)
                .build();

        given(purchaseRepository.findByMerchantUid(merchantUid)).willReturn(Mono.just(purchase));

        PurchaseCancelRequest request = new PurchaseCancelRequest();
        request.setMerchantUid(merchantUid);

        // when
        Throwable ex = catchThrowable(() -> purchaseService.cancelPayment(request, userId).block());

        // then
        assertThat(ex).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void PortOne_응답이_cancelled가_아니면_BAD_REQUEST_예외가_발생한다() {
        // given
        Long userId = 1L;
        String merchantUid = "merchant-123";

        Purchase purchase = Purchase.builder()
                .id(1L)
                .userId(userId)
                .merchantUid(merchantUid)
                .impUid("imp-001")
                .productId(10L)
                .productPrice(10000)
                .paidAmount(10000)
                .paymentMethod("card")
                .status(Status.COMPLETED)
                .createdAt(Instant.now())
                .isVerified(true)
                .build();

        PortOneCancelResponse response = new PortOneCancelResponse(
                0, "success",
                new PortOneCancelResponse.CancelResponse(
                        "imp-001", merchantUid, 10000, "failed" // status가 cancelled 아님
                )
        );

        given(purchaseRepository.findByMerchantUid(merchantUid)).willReturn(Mono.just(purchase));
        given(portOneClient.cancelPayment("imp-001", 10000)).willReturn(Mono.just(response));

        PurchaseCancelRequest request = new PurchaseCancelRequest();
        request.setMerchantUid(merchantUid);

        // when
        Throwable ex = catchThrowable(() -> purchaseService.cancelPayment(request, userId).block());

        // then
        assertThat(ex).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void DB저장실패시_saveCancelFailure_호출흐름_탐지된다() {
        // given
        Long userId = 1L;
        String merchantUid = "merchant-123";
        int paidAmount = 10000;

        Purchase purchase = Purchase.builder()
                .id(1L)
                .userId(userId)
                .merchantUid(merchantUid)
                .impUid("imp-001")
                .productId(10L)
                .productPrice(paidAmount)
                .paidAmount(paidAmount)
                .paymentMethod("card")
                .status(Status.COMPLETED)
                .createdAt(Instant.now())
                .isVerified(true)
                .build();

        PortOneCancelResponse response = new PortOneCancelResponse(
                0, "success",
                new PortOneCancelResponse.CancelResponse(
                        "imp-001", merchantUid, paidAmount, "cancelled"
                )
        );

        // PortOne 취소 성공, DB 저장 실패 → saveCancelFailure 호출 흐름으로
        given(purchaseRepository.findByMerchantUid(merchantUid)).willReturn(Mono.just(purchase));
        given(portOneClient.cancelPayment("imp-001", paidAmount)).willReturn(Mono.just(response));
        given(purchaseRepository.save(any(Purchase.class))).willReturn(Mono.error(new RuntimeException("DB 오류")));
        given(cancelFailureRepository.save(any())).willReturn(Mono.empty());

        PurchaseCancelRequest request = new PurchaseCancelRequest();
        request.setMerchantUid(merchantUid);

        // when
        Throwable ex = catchThrowable(() -> purchaseService.cancelPayment(request, userId).block());

        // then
        assertThat(ex).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400);
    }

}
