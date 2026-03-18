package com.example.smu_club.club.dto;


import com.example.smu_club.answer.dto.AnswerResponseDto;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ApplicantDetailViewResponse {

    private ApplicantInfoResponse applicantInfo;
    private List<AnswerResponseDto> applicationForm;
    private String fileKeyUrl;
}
