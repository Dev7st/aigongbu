package org.example.paymentservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class ClientConfig {

    @Bean
    WebClient webClient(
            ClientProperties clientProperties,
            WebClient.Builder webClientBuilder
    ) {
        return webClientBuilder
                .baseUrl(String.valueOf(clientProperties.portoneUri()))
                .build();
    }

    @Bean
    WebClient lectureWebClient(
            ClientProperties clientProperties,
            WebClient.Builder webClientBuilder
    ) {
        return webClientBuilder
                .baseUrl(String.valueOf(clientProperties.lectureUri()))
                .build();
    }
}