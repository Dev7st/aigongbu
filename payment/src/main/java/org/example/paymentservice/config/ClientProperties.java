package org.example.paymentservice.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties(prefix = "webclient")
public record ClientProperties(
        @NotNull
        URI portoneUri,
        @NotNull
        URI lectureUri
) {}
