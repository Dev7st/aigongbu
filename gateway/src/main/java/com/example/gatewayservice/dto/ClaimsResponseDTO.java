package com.example.gatewayservice.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ClaimsResponseDTO {
    private Long id;
    private String nickname;
    private String profileImage;
}
