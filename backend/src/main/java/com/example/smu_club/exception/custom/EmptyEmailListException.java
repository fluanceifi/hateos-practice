package com.example.smu_club.exception.custom;

public class EmptyEmailListException extends RuntimeException {
    public EmptyEmailListException(String message) {
        super(message);
    }
}
