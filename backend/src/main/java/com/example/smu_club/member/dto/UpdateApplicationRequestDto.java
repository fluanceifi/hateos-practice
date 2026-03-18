package com.example.smu_club.member.dto;


import lombok.Getter;

import java.util.List;

@Getter
public class UpdateApplicationRequestDto {

    //답변들, 파일
    private List<UpdateAnswerRequestDto> answers;
    private String fileKey;
}
