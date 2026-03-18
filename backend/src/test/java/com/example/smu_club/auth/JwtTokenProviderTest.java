package com.example.smu_club.auth;


import com.example.smu_club.auth.jwt.JwtTokenProvider;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.Key;
import java.util.Date;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    private final String secretKey = "egp2qpSlYHOYWOTV/grGlJtmvvwamjIc8SAvzvx7B+g=";
    private final long accessTokenValidity = 1800; // 30분
    private final long refreshTokenValidity = 604800; // 7일
    private Key key;

    @BeforeEach
    void setUp() {
        this.jwtTokenProvider = new JwtTokenProvider(secretKey, accessTokenValidity, refreshTokenValidity);

        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    private String createExpiredToken() {
        long now = (new Date()).getTime();
        // 만료 시간을 현재보다 1초 '전'으로 설정
        Date expiredDate = new Date(now - 1000);

        return Jwts.builder()
                .setSubject("test-user")
                .setExpiration(expiredDate) // 과거의 만료 시간
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}
