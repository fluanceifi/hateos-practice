package com.example.smu_club.member.dto;

import com.example.smu_club.domain.ClubMemberStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApplicationResultResponseDto {
    private Long memberId;
    private Long clubId;
    private String memberName;
    private String clubName;
    private ClubMemberStatus status;
}
