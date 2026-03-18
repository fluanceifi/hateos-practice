package com.example.smu_club.domain;

import lombok.Getter;

@Getter
public enum RecruitingStatus {
    OPEN(1),
    CLOSED(2);

    private final int priority;

    RecruitingStatus(int priority){
        this.priority = priority;
    }
}
