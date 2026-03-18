package com.example.smu_club.auth.dto;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL) // json 변환시 null필드 제외
public class LoginResponse {

    private String status;
    private String studentId;
    private String name;
}
