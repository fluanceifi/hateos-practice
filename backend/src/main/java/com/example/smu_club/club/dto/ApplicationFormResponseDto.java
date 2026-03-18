package com.example.smu_club.club.dto;

import com.example.smu_club.question.dto.QuestionResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;


@Getter
@AllArgsConstructor
public class ApplicationFormResponseDto {
    private Long memberId;
    private String studentId;
    private String name;
    private String department;
    private String phone;
    private List<QuestionResponse> questions;

}
