package com.example.smu_club.auth.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
@AllArgsConstructor
public class JwtTokenResponse {

    private String grantType; // "Bearer"
    private String accessToken;
    private String refreshToken;
}
