package com.example.gatewayservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "spring.cloud.gateway.whitelist")
@Getter
@Setter
public class WhitelistProperties {
    private List<String> paths;
}