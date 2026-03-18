package com.example.smu_club.auth.dto;


import lombok.Getter;

@Getter
public class ReissueRequest {
    private String accessToken;
    private String refreshToken;
}
