package com.example.gatewayservice.config.client;

import com.example.gatewayservice.dto.RefreshTokenResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AuthServiceClient {

    private final WebClient authClient;

    public Mono<RefreshTokenResponseDTO> refreshToken(String refreshToken) {
        return authClient.post()
                .uri("/auths/refresh-token")
                .header("Authorization", "Bearer " + refreshToken)
                .cookie("refreshToken", refreshToken)
                .retrieve()
                .bodyToMono(RefreshTokenResponseDTO.class);
    }
}
