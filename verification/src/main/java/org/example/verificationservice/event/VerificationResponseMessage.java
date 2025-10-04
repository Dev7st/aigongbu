package org.example.verificationservice.event;

public record VerificationResponseMessage(
        Long purchaseId,
        boolean isValid,
        String reason
) {}
