package com.example.smu_club.util;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class PreSignedUrlResponse {

    private final String fileName;
    private final String preSignedUrl;

}
