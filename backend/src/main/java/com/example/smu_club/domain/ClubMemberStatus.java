package com.example.smu_club.domain;

import lombok.Getter;

@Getter
public enum ClubMemberStatus {
    ACCEPTED("합격"),
    REJECTED("불합격"),
    PENDING("대기중");

    private final String description;

    ClubMemberStatus(String description) {
        this.description = description;
    }
}
