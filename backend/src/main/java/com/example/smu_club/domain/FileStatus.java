package com.example.smu_club.domain;

public enum FileStatus {

    PENDING, // URL 발급됐으나 저장 전 (고아후보)
    ACTIVE, // 서비스 연결되어 사용중
    DELETED // 수정되어서 삭제되어야 할 상태
}
