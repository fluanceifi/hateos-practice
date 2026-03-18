package com.example.smu_club.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Role {
    MEMBER("ROLE_MEMBER", "일반 회원"),
    OWNER("ROLE_OWNER", "클럽장"),
    ADMIN("ROLE_ADMIN", "관리자");

    // 위에서 만든 생성자를 만들어주는 역할
    private final String key;
    private final String title;

}
