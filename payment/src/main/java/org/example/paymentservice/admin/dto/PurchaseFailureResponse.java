package org.example.paymentservice.admin.dto;

import org.example.paymentservice.purchase.entity.Purchase;
import org.example.paymentservice.purchase.type.Status;

import java.time.Instant;

public record PurchaseFailureResponse(
        Long id,
        String merchantUid,
        String impUid,
        Status status,
        String reason,
        Instant createdAt
) {
    public static PurchaseFailureResponse getFailure(Purchase purchase) {
        return new PurchaseFailureResponse(
                purchase.id(),
                purchase.merchantUid(),
                purchase.impUid(),
                purchase.status(),
                purchase.reason(),
                purchase.createdAt()
        );
    }
}
