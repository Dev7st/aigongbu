package org.example.paymentservice.portone;

public record PortOneTokenResponse(
        TokenResponse response
) {
    public record TokenResponse(
            String access_token,
            long now,
            long expired_at
    ) {}
}
