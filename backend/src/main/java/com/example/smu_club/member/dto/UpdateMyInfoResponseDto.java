package com.example.smu_club.member.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UpdateMyInfoResponseDto {
    private long memberId; //내정보 수정 할때 식별용
    private String email;
    private String phoneNumber;
}
