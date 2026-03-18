package com.example.smu_club.club.dto;

import com.example.smu_club.domain.RecruitingStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Getter
public class ManagedClubResponse {
    private Long id;
    private String name;
    private RecruitingStatus recruitingStatus;
}
