package com.example.smu_club.auth.controller;


import com.example.smu_club.auth.dto.JwtTokenResponse;
import com.example.smu_club.auth.dto.LoginRequest;
import com.example.smu_club.auth.dto.ReissueRequest;
import com.example.smu_club.auth.dto.SignupRequest;
import com.example.smu_club.auth.service.AuthService;
import com.example.smu_club.auth.service.TokenService;
import com.example.smu_club.common.ApiResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@Slf4j
@RestController
@RequestMapping("/api/v1/public/auth")
@RequiredArgsConstructor
public class PublicAuthController {

    private final AuthService authService;
    private final TokenService tokenService;

    // 로그인시도
    @PostMapping("/login")
    public ResponseEntity<ApiResponseDto<JwtTokenResponse>> login(@RequestBody LoginRequest loginRequest) {
        log.info("사용자 로그인 시도 :{}", loginRequest.getStudentId());

        JwtTokenResponse jwtTokenResponse = authService.login(loginRequest);
        ApiResponseDto<JwtTokenResponse> response = ApiResponseDto.success(jwtTokenResponse, "로그인에 성공했습니다.");
        return ResponseEntity.ok(response);

    }

    // 사용자의 accessToken 이 만료되었을 때 이 API 호출을 통해서 RefreshToken 을 통해서 accessToken 을 재발급
    @PostMapping("/reissue")
    public ResponseEntity<ApiResponseDto<JwtTokenResponse>> reissue(@RequestBody ReissueRequest reissueRequest) {
        JwtTokenResponse jwtTokenResponse = tokenService.reissueTokens(reissueRequest);

        ApiResponseDto<JwtTokenResponse> response = ApiResponseDto.success(jwtTokenResponse, "토큰이 성공적으로 발급되었습니다.");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponseDto<Void>> signup(@RequestBody SignupRequest signupRequest) {
        log.info("신규 사용자 회원가입 시도 :{}", signupRequest.getStudentId());
        authService.signUp(signupRequest);

        ApiResponseDto<Void> response = ApiResponseDto.success("회원가입이 완료되었습니다.");

        return ResponseEntity.status(HttpStatus.CREATED).body(response);

    }

}
