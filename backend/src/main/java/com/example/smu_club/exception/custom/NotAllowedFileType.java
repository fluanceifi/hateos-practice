package com.example.smu_club.exception.custom;

public class NotAllowedFileType extends RuntimeException {
    public NotAllowedFileType(String message) {
        super(message);
    }
}
