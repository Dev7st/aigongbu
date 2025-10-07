package com.example.gatewayservice.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@Configuration
public class WebEndpoints {

    @Bean
    public RouterFunction<ServerResponse> routes() {
        return RouterFunctions.route()
                .path("/fallback/static", builder -> builder
                        .GET(this::fallback)
                        .POST(this::fallback)
                        .PUT(this::fallback)
                        .DELETE(this::fallback)
                )
                .build();
    }

    private Mono<ServerResponse> fallback(ServerRequest request) {
        String method = request.methodName();
        String message = String.format("{\"message\": \"[%s] 현재 서비스가 일시적으로 지연되고 있습니다.\"}", method);
        return ServerResponse
                .status(SERVICE_UNAVAILABLE)
                .contentType(APPLICATION_JSON)
                .bodyValue(message);
    }
}
