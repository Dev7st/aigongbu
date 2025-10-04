package org.example.verificationservice.portone;

import lombok.RequiredArgsConstructor;
import org.example.verificationservice.config.PortOneProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class PortOneClient {

    private final WebClient webClient;
    private final PortOneProperties portOneProperties;

    public Mono<PortOneVerifyResponse> verifyPayment(String impUid) {
        return getAccessToken()
                .flatMap(token ->
                        webClient.get()
                                .uri("/payments/" + impUid)
                                .header("Authorization", "Bearer " + token)
                                .retrieve()
                                .bodyToMono(PortOneVerifyResponse.class)
                );
    }

    private Mono<String> getAccessToken() {
        return webClient.post()
                .uri("/users/getToken")
                .bodyValue(Map.of(
                        "imp_key", portOneProperties.impKey(),
                        "imp_secret", portOneProperties.impSecret()
                ))
                .retrieve()
                .bodyToMono(PortOneTokenResponse.class)
                .map(token -> token.response().access_token());
    }

}
