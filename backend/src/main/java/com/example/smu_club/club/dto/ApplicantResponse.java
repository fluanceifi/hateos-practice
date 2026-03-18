package com.example.smu_club.club.dto;


import com.example.smu_club.domain.ClubMember;
import com.example.smu_club.domain.ClubMemberStatus;
import com.example.smu_club.domain.Member;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
@RequiredArgsConstructor
public class ApplicantResponse {

    private final Long clubMemberId;
    private final Long memberId;
    private final String name;
    private final String studentId;
    private final LocalDateTime appliedAt;
    private final ClubMemberStatus status;

    public static ApplicantResponse from(ClubMember clubMember) {
        Member member = clubMember.getMember();
        return ApplicantResponse.builder()
                .clubMemberId(clubMember.getId())
                .memberId(member.getId())
                .name(member.getName())
                .studentId(member.getStudentId())
                .appliedAt(clubMember.getAppliedAt())
                .status(clubMember.getStatus())
                .build();
    }
}
