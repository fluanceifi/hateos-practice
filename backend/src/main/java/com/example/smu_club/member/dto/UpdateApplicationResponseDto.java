package com.example.smu_club.member.dto;

import com.example.smu_club.answer.dto.AnswerResponseDto;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class UpdateApplicationResponseDto {
    //내정보
    private Long memberId;
    private String studentId;
    private String department;
    private String name;
    private String phone;

    //질문, 답변, 파일 키
    private final List<AnswerResponseDto> QuestionAndAnswer;
    private final String fileKeyUrl;
}
