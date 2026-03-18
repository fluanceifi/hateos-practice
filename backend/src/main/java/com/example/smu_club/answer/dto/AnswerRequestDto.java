package com.example.smu_club.answer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AnswerRequestDto {
    @NotNull
    Long questionId;
    String orderNum;
    @NotBlank(message = "질문 내용은 비워둘 수 없습니다.")
    String questionContent;
    String answerContent;
}
