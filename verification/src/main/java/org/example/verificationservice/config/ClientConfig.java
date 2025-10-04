package org.example.verificationservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class ClientConfig {

    @Bean
    WebClient webClient(@Value("${webclient.portone-uri}") String portOneUri) {
        return WebClient.builder()
                .baseUrl(portOneUri)
                .build();
    }

}
