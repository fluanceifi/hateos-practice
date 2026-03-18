package com.example.smu_club.answer.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AnswerResponseDto {
    Long questionId;
    int orderNum;
    String questionContent;
    String answerContent;
}
