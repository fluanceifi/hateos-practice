package com.example.smu_club.exception.custom;

public class UnivAuthenticationFailedException extends RuntimeException {
    public UnivAuthenticationFailedException(String message) {
        super(message);
    }
}
