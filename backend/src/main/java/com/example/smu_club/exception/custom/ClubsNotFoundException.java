package com.example.smu_club.exception.custom;

public class ClubsNotFoundException extends RuntimeException {
    public ClubsNotFoundException(String message) {
        super(message);
    }
}
