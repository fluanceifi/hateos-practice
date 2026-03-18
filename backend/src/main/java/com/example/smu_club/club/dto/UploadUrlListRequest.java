package com.example.smu_club.club.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class UploadUrlListRequest {

    private List<FileDetail> files;

    @Getter
    public static class FileDetail {
        private String fileName;
        private String contentType;
    }

}
