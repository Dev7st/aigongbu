package org.example.paymentservice.purchase.dto;

import org.example.paymentservice.lecture.LectureInfo;
import org.example.paymentservice.purchase.entity.Purchase;

import java.time.Instant;

public record PurchaseSummaryResponse(
        String merchantUid,
        Long productId,
        String productTitle,
        String instructor,
        String status,
        int paidAmount,
        String paymentMethod,
        Instant createdAt
) {
    public static PurchaseSummaryResponse of(Purchase purchase, LectureInfo lecture) {
        return new PurchaseSummaryResponse(
                purchase.merchantUid(),
                purchase.productId(),
                lecture.title(),
                lecture.instructorName(),
                purchase.status().name(),
                purchase.paidAmount(),
                purchase.paymentMethod(),
                purchase.createdAt()
        );
    }
}
