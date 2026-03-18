package com.example.smu_club.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UnivAuthRequest {
    private String username;
    private String password;
}
