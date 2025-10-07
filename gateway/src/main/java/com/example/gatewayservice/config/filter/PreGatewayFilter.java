package com.example.gatewayservice.config.filter;

import com.example.gatewayservice.config.WhitelistProperties;
import com.example.gatewayservice.config.client.AuthServiceClient;
import com.example.gatewayservice.config.jwt.TokenProvider;
import com.example.gatewayservice.dto.ClaimsResponseDTO;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

@Slf4j
@Order(0)
@Component
public class PreGatewayFilter extends AbstractGatewayFilterFactory<PreGatewayFilter.Config> {

    private final TokenProvider tokenProvider;
    private final RedisTemplate<String, String> redisTemplate;
    private final AuthServiceClient authServiceClient;

    public PreGatewayFilter(TokenProvider tokenProvider,
                            RedisTemplate<String, String> redisTemplate,
                            AuthServiceClient authServiceClient) {
        super(Config.class);
        this.tokenProvider = tokenProvider;
        this.redisTemplate = redisTemplate;
        this.authServiceClient = authServiceClient;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String token = exchange.getRequest().getHeaders().getFirst(AUTHORIZATION);
            String path = exchange.getRequest().getURI().getPath();

            // ✅ 인증 없이 통과할 경로 예외 처리
            if (path.matches("^/lectures/video/\\d+/stream$")) {
                return chain.filter(exchange);
            }

            if (token == null || !token.toLowerCase().startsWith(config.getTokenPrefix().toLowerCase())) {
                log.warn("Authorization 헤더 누락 또는 Bearer 없음");
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            String pureToken = token.substring(config.getTokenPrefix().length());
            int status = tokenProvider.validateToken(pureToken);

            if (status == 2) { // 만료
                log.warn("토큰 만료 → refreshToken 요청 시도");
                String refreshToken = extractRefreshTokenFromCookie(exchange.getRequest());

                if (refreshToken == null) {
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }

                return authServiceClient.refreshToken(refreshToken)
                        .flatMap(dto -> {
                            if (!dto.isSuccess()) {
                                log.warn("refresh 실패: {}", dto.getMessage());

                                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                                exchange.getResponse().getHeaders().add("Content-Type", "application/json");

                                String json = String.format("{\"success\":false,\"message\":\"%s\"}", dto.getMessage());
                                return exchange.getResponse().writeWith(
                                        Mono.just(exchange.getResponse().bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8)))
                                );
                            }

                            // 성공 로직은 동일
                            ClaimsResponseDTO claims = tokenProvider.getAuthentication(dto.getAccessToken());
                            ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                                    .header("X-User-Token", dto.getAccessToken())
                                    .header("X-User-Id", String.valueOf(claims.getId()))
                                    .header("X-User-Nickname", claims.getNickname())
                                    .header("X-User-ProfileImage", claims.getProfileImage())
                                    .build();

                            return chain.filter(exchange.mutate().request(modifiedRequest).build());
                        })
                        .onErrorResume(e -> {
                            log.error("refresh-token 호출 중 예외 발생", e);

                            exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
                            exchange.getResponse().getHeaders().add("Content-Type", "application/json");

                            String json = "{\"success\":false,\"message\":\"서버 오류로 인해 토큰을 재발급하지 못했습니다.\"}";
                            return exchange.getResponse().writeWith(
                                    Mono.just(exchange.getResponse().bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8)))
                            );
                        });
            }

            if (status != 1) {
                log.error("토큰 검증 실패 또는 기타 예외");
                exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
                return exchange.getResponse().setComplete();
            }

            ClaimsResponseDTO claims = tokenProvider.getAuthentication(pureToken);
            String savedToken = redisTemplate.opsForValue().get("accessToken:" + claims.getId());

            if (savedToken == null || !savedToken.equals(pureToken)) {
                log.warn("Redis 저장 토큰과 요청 토큰 불일치");
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                    .header("X-User-Token", pureToken)
                    .header("X-User-Id", String.valueOf(claims.getId()))
                    .header("X-User-Nickname", claims.getNickname())
                    .header("X-User-ProfileImage", claims.getProfileImage())
                    .build();

            return chain.filter(exchange.mutate().request(modifiedRequest).build());
        };
    }

    private String extractRefreshTokenFromCookie(ServerHttpRequest request) {
        return request.getCookies().getFirst("refreshToken") != null
                ? request.getCookies().getFirst("refreshToken").getValue()
                : null;
    }

    @Getter
    @Setter
    public static class Config {
        private String tokenPrefix = "Bearer ";
        private int authenticationTimeoutCode = 419;
    }
}
