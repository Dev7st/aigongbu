package org.example.paymentservice.admin.dto;

import org.example.paymentservice.purchase.entity.CancelFailure;

import java.time.Instant;

public record CancelFailureResponse(
        Long id,
        Long purchaseId,
        String impUid,
        Integer amount,
        String reason,
        Instant createdAt
) {
    public static CancelFailureResponse getFailure(CancelFailure cancelFailure) {
        return new CancelFailureResponse(
                cancelFailure.id(),
                cancelFailure.purchaseId(),
                cancelFailure.impUid(),
                cancelFailure.amount(),
                cancelFailure.reason(),
                cancelFailure.createdAt()
        );
    }
}