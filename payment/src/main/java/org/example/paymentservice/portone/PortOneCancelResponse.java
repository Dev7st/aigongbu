package org.example.paymentservice.portone;

public record PortOneCancelResponse(
        int code,
        String message,
        CancelResponse response
) {
    public record CancelResponse(
            String imp_uid,
            String merchant_uid,
            int amount,
            String status
    ) {}
}
