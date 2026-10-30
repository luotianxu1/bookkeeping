package com.example.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

import static io.jsonwebtoken.security.Keys.hmacShaKeyFor;

@Service
public class JwtTokenService {

    private final SecretKey secretKey;

    public JwtTokenService(@Value("${app.jwt.secret}") String secret) {
        this.secretKey = hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Claims parse(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
