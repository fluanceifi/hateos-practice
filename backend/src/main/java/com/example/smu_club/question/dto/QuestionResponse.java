package com.example.smu_club.question.dto;

import com.example.smu_club.domain.QuestionContentType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class QuestionResponse {
    private final Long questionId;
    private final int orderNum;
    private final String content;
}
