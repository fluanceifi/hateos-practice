package com.example.smu_club.exception.custom;

public class ClubMemberNotFoundException extends RuntimeException {
    public ClubMemberNotFoundException(String message) {
        super(message);
    }
}
