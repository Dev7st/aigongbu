package org.example.paymentservice.event;

public record RollBackedMessage(
        Long purchaseId,
        boolean isRollBacked,
        String reason
) {}
