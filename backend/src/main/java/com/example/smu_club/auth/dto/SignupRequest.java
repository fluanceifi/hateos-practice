package com.example.smu_club.auth.dto;


import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SignupRequest {

    private String studentId;
    private String password;
    private String phoneNumber;
}
