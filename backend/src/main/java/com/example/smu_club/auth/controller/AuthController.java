package com.example.smu_club.auth.controller;


import com.example.smu_club.auth.service.AuthService;
import com.example.smu_club.common.ApiResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth/")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/logout")
    public ResponseEntity<ApiResponseDto<Void>> logout(@AuthenticationPrincipal User user) {

        String studentId = user.getUsername();
        authService.logout(studentId);

        ApiResponseDto<Void> response = ApiResponseDto.success("성공적으로 로그아웃 되었습니다.");
        return ResponseEntity.ok(response);
    }
}
