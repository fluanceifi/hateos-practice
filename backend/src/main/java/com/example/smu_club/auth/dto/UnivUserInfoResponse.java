package com.example.smu_club.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UnivUserInfoResponse {
    private String username;
    private String name;
    private String email;
    private String department;
    private String secondDepartment;
}
