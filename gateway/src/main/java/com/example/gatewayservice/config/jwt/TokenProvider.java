package com.example.gatewayservice.config.jwt;

import com.example.gatewayservice.dto.ClaimsResponseDTO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class TokenProvider {

    private final JwtProperties jwtProperties;

    public int validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(getSecretKey()).build().parseClaimsJws(token);
            return 1;
        } catch (ExpiredJwtException e) {
            return 2;
        } catch (Exception e) {
            return 3;
        }
    }

    public ClaimsResponseDTO getAuthentication(String token) {
        Claims claims = getClaims(token);
        return ClaimsResponseDTO.builder()
                .id(Long.valueOf(claims.getSubject()))
                .nickname(claims.get("nickname", String.class))
                .profileImage(claims.get("profileImage", String.class))
                .build();
    }

    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSecretKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private SecretKey getSecretKey() {
        byte[] keyBytes = Base64.getDecoder().decode(jwtProperties.getSecretKey());
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
