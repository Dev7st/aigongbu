package org.example.paymentservice.portone;

public record PortOneVerifyResponse(
        int code,
        String message,
        VerifyResponse response
) {
    public record VerifyResponse(
            String imp_uid,
            String merchant_uid,
            int amount
    ) {}
}
