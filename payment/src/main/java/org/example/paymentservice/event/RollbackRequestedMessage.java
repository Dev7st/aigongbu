package org.example.paymentservice.event;

public record RollbackRequestedMessage(
        Long purchaseId,
        String impUid,
        int paidAmount,
        Long productId
) {}
