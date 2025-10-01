package org.example.paymentservice.event;

public record VerifyRequestedMessage(
        Long purchaseId,
        String impUid,
        int paidAmount
) {}
