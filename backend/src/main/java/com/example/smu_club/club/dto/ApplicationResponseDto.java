package com.example.smu_club.club.dto;

import com.example.smu_club.answer.dto.AnswerRequestDto;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ApplicationResponseDto {
    private final List<AnswerRequestDto> QuestionAndAnswer;
    private final String fileKey;
}
