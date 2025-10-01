package org.example.paymentservice.event;

public record VerifiedMessage(
        Long purchaseId,
        boolean isValid,
        String reason
) {}
