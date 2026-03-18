package com.example.smu_club.club.dto;

import com.example.smu_club.domain.ClubMember;
import com.example.smu_club.domain.Member;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ApplicantExcelDto {

    private final String name;
    private final String studentId;
    private final String phoneNumber;
    private final String email;
    private final String status;

    public static ApplicantExcelDto from(ClubMember clubMember) {
        Member member = clubMember.getMember();

        return ApplicantExcelDto.builder()
                .name(member.getName())
                .studentId(member.getStudentId())
                .phoneNumber(member.getPhoneNumber())
                .email(member.getEmail())
                .status(clubMember.getStatus().getDescription())
                .build();
    }
}