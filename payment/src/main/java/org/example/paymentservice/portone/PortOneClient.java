package org.example.paymentservice.portone;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.paymentservice.config.PortOneProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PortOneClient {

    private final WebClient webClient;
    private final PortOneProperties portOneProperties;

    private Mono<String> getAccessToken() {
        log.info("imp_key={}, imp_secret={}", portOneProperties.impKey(), portOneProperties.impSecret());
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

    public Mono<PortOneCancelResponse> cancelPayment(String impUid, int amount) {
        return getAccessToken()
                .flatMap(token ->
                        webClient.post()
                                .uri("/payments/cancel")
                                .header("Authorization", "Bearer " + token)
                                .bodyValue(Map.of(
                                        "imp_uid", impUid,
                                        "amount", amount
                                        ))
                                .retrieve()
                                .bodyToMono(PortOneCancelResponse.class)
                );
    }

    public Mono<PortOnePaymentResponse> getPaymentByImpUid(String impUid) {
        return getAccessToken()
                .flatMap(token ->
                        webClient.get()
                                .uri("/payments/{imp_uid}", impUid)
                                .header("Authorization", "Bearer " + token)
                                .retrieve()
                                .bodyToMono(PortOneGetPaymentResponse.class)
                                .map(PortOneGetPaymentResponse::getResponse)
                );
    }

    public Mono<List<PortOnePaymentResponse>> getPaidPaymentsWithinPeriod(LocalDateTime from, LocalDateTime to) {
        return getAccessToken()
                .flatMap(token ->
                        webClient.get()
                                .uri(uriBuilder -> uriBuilder
                                        .path("/payments/status/paid")
                                        .queryParam("from", from.toEpochSecond(ZoneOffset.UTC))
                                        .queryParam("to", to.toEpochSecond(ZoneOffset.UTC))
                                        .build())
                                .header("Authorization", "Bearer " + token)
                                .retrieve()
                                .bodyToMono(new ParameterizedTypeReference<PortOneGetListResponse>() {})
                                .map(PortOneGetListResponse::getResponse)
                                .map(PortOneGetListResponse.Response::getList)
                );
    }
}
