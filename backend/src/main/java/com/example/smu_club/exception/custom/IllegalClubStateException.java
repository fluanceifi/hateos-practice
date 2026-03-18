package com.example.smu_club.exception.custom;

public class IllegalClubStateException extends RuntimeException {
    public IllegalClubStateException(String message) {
        super(message);
    }
}
