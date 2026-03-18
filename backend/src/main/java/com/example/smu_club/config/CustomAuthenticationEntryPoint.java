package com.example.smu_club.config;

import com.example.smu_club.common.ApiResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import org.springframework.security.core.AuthenticationException;
import java.io.IOException;
import java.io.OutputStream;


@Component
@Slf4j
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        log.error("Authentication Error: {}", authException.getMessage());

        String errorCode = (String) request.getAttribute("exception");

        if (errorCode == null) {
            setResponse(response, "UNAUTHORIZED", "인증이 필요합니다.");
            return;
        }

        if (errorCode.equals("EXPIRED_TOKEN")) {
            setResponse(response, "EXPIRED_TOKEN", "Access Token이 만료되었습니다.");
        } else if (errorCode.equals("INVALID_TOKEN")) {
            setResponse(response, "INVALID_TOKEN", "유효하지 않은 토큰입니다.");
        }
    }

    private void setResponse(HttpServletResponse response, String errorCode, String message) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        ApiResponseDto<Object> responseDto = ApiResponseDto.fail(errorCode, message);

        try (OutputStream os = response.getOutputStream()) {
            objectMapper.writeValue(os, responseDto);
            os.flush();
        }
    }
}
