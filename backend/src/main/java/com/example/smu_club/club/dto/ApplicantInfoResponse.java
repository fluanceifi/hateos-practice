package com.example.smu_club.club.dto;

import com.example.smu_club.domain.ClubMemberStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;


@Getter
@Builder
public class ApplicantInfoResponse {

    private Long clubMemberId;
    private Long memberId;
    private String name;
    private String studentId;
    private String department;
    private String phoneNumber;
    private String email;
    private LocalDateTime appliedAt;
    private ClubMemberStatus status;

}
