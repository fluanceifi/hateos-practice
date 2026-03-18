package com.example.smu_club.member.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;


@Data
public class UpdateMyPhoneNumberRequestDto {
    @NotBlank(message = "새로운 전화번호는 공백일 수 없습니다.")
    private String newPhoneNumber;
}
