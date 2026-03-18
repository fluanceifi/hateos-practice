package com.example.smu_club.exception.custom;

public class DuplicateClubNameException extends RuntimeException {

    public DuplicateClubNameException(String message) {
        super(message);
    }
}
