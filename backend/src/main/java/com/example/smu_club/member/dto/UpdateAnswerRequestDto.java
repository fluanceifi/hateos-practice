package com.example.smu_club.member.dto;


import lombok.Getter;

@Getter
public class UpdateAnswerRequestDto {
    private long questionId;
    private String answerContent;
}
