package com.example.smu_club.config;

import com.example.smu_club.auth.jwt.JwtTokenProvider;
import com.example.smu_club.exception.custom.ExpiredTokenException;
import com.example.smu_club.exception.custom.InvalidTokenException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final  String BEARER_PREFIX = "Bearer ";
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {

        String jwt = resolveToken(request);

        try {
            if (StringUtils.hasText(jwt)) {
                Authentication authentication = jwtTokenProvider.getAuthentication(jwt);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (ExpiredTokenException e) {
            log.warn("엑세스 토큰이 만료되었습니다.  URI: {}, {}", request.getRequestURI(), e.getMessage());
            request.setAttribute("exception", "EXPIRED_TOKEN");
        } catch (InvalidTokenException e) {
            // [다음 단계] 여기에 유효하지 않은 Access Token 에러 응답 생성 로직 추가
            log.warn("유효하지 않은 엑세스 토큰입니다. URI: {}, {}", request.getRequestURI(), e.getMessage());
            request.setAttribute("exception", "INVALID_TOKEN");
        }

        filterChain.doFilter(request, response);

    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
