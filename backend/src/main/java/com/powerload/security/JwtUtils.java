package com.powerload.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtils {

    private final SecretKey key;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    public JwtUtils(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-expiration-ms:1800000}") long accessExpirationMs,
            @Value("${jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs) {
        if (secret == null || secret.isBlank() || secret.length() < 32) {
            throw new IllegalStateException("jwt.secret 未配置或长度不足 32 字符，必须从环境变量提供");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public String generateAccessToken(Long userId, String username, String role) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .claim("type", "ACCESS")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessExpirationMs))
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(Long userId, String username) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("type", "REFRESH")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpirationMs))
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
    }

    public boolean isAccessToken(Claims claims) {
        return "ACCESS".equals(claims.get("type"));
    }

    public boolean isRefreshToken(Claims claims) {
        return "REFRESH".equals(claims.get("type"));
    }

    public Long getUserId(Claims claims) {
        return Long.parseLong(claims.getSubject());
    }

    public String getUsername(Claims claims) {
        return claims.get("username", String.class);
    }

    public String getRole(Claims claims) {
        return claims.get("role", String.class);
    }
}
