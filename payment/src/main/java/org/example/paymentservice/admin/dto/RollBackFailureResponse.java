package org.example.paymentservice.admin.dto;

import org.example.paymentservice.purchase.entity.RollBackFailure;

import java.time.Instant;

public record RollBackFailureResponse(
        Long id,
        Long purchaseId,
        String impUid,
        int amount,
        String reason,
        Instant createdAt
) {
    public static RollBackFailureResponse getFailure(RollBackFailure failure) {
        return new RollBackFailureResponse(
                failure.id(),
                failure.purchaseId(),
                failure.impUid(),
                failure.amount(),
                failure.reason(),
                failure.createdAt()
        );
    }
}
