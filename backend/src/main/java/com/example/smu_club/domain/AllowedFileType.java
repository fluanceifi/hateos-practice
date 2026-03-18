package com.example.smu_club.domain;

import lombok.Getter;
import java.util.List;

@Getter
public enum AllowedFileType {
    // 이미지 (이미지는 보통 하나지만 통일성을 위해 List 사용)
    JPG("jpg", List.of("image/jpeg")),
    JPEG("jpeg", List.of("image/jpeg")),
    PNG("png", List.of("image/png")),
    WEBP("webp", List.of("image/webp")),

    // 문서
    PDF("pdf", List.of("application/pdf")),
    DOCX("docx", List.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
    HWP("hwp", List.of("application/x-hwp", "application/haansofthwp", "application/octet-stream")),
    HWPX("hwpx", List.of("application/vnd.hancom.hwpx", "application/haansofthwp", "application/octet-stream")),
    PPTX("pptx", List.of("application/vnd.openxmlformats-officedocument.presentationml.presentation")),
    XLSX("xlsx", List.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

    private final String extension;
    private final List<String> mimeTypes;

    AllowedFileType(String extension, List<String> mimeTypes) {
        this.extension = extension;
        this.mimeTypes = mimeTypes;
    }

    public static boolean isMatched(String extension, String mimeType) {
        for(AllowedFileType type : AllowedFileType.values()) {
            if(type.getExtension().equalsIgnoreCase(extension) &&
                    type.getMimeTypes().contains(mimeType)) {
                return true;
            }
        }
        return false;
    }
}