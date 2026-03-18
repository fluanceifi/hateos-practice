package com.example.smu_club.club.dto;

import com.example.smu_club.answer.dto.AnswerRequestDto;
import lombok.*;

import java.util.List;

@Data
public class ApplicationRequestDto {
    private List<AnswerRequestDto> QuestionAndAnswer;
    private String fileKey; //UUID 포함 파일명
}
