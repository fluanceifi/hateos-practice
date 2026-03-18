package com.example.smu_club.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
public class SecurityContextDebugFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 요청이 들어올 때마다 SecurityContext의 상태를 로그로 출력합니다.
        log.info("===== SecurityContextDebugFilter START for URI: {} =====", request.getRequestURI());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            log.warn("SecurityContext에 Authentication 객체가 없습니다. (인증되지 않음)");
        } else {
            log.info("Authentication 객체 타입: {}", authentication.getClass().getName());
            log.info("Principal (사용자 정보): {}", authentication.getPrincipal());
            log.info("Authorities (권한 목록): {}", authentication.getAuthorities());
            log.info("Authenticated 상태: {}", authentication.isAuthenticated());
        }

        log.info("==========================================================");

        // 다음 필터로 요청을 전달합니다.
        filterChain.doFilter(request, response);
    }
}