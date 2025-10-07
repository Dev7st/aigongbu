package com.example.gatewayservice.config.redis;

import com.example.gatewayservice.config.jwt.TokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component("smartKeyResolver")
@RequiredArgsConstructor
public class SmartKeyResolver implements KeyResolver {

    private final TokenProvider tokenProvider;

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Long userId = tokenProvider.getAuthentication(token).getId();
                return Mono.just("user:" + userId); // ✅ 유저별 제한
            } catch (Exception e) {
                // 토큰 파싱 실패 → fallback to IP
            }
        }

        String ip = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        return Mono.just("ip:" + ip);
    }
}
