package com.example.smu_club.auth.external;


import com.example.smu_club.auth.dto.UnivAuthRequest;
import com.example.smu_club.auth.dto.UnivUserInfoResponse;
import com.example.smu_club.exception.custom.UnivAuthenticationFailedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!test")
public class UnivApiClientImpl implements UnivApiClient {

    private final RestClient restClient;

    @CircuitBreaker(name = "univApi", fallbackMethod = "authenticateFallback")
    @Override
    public UnivUserInfoResponse authenticate(String studentId, String password) {

        UnivAuthRequest requestPayload = new UnivAuthRequest(studentId, password);

        try {
            return restClient.post()
                    .uri("/auth")
                    .body(requestPayload)
                    .retrieve()
                    .body(UnivUserInfoResponse.class);
        } catch (RestClientResponseException e) {
            log.warn("학교 인증 실패, 학번: {}, 상태코드: {}", studentId, e.getStatusCode());

            if (e.getStatusCode().is4xxClientError()) {
                return null; // <-- 4XX(비즈니스 오류) 시 null 반환
            }

            throw e;
        }
    }

    private UnivUserInfoResponse authenticateFallback(String studentId, String password,Throwable e) {

        log.warn("서킷 브레이커 작동 Fallback, 학번 : {}, 원인 : {}", studentId, e.getMessage());

        throw new UnivAuthenticationFailedException("현재 외부 인증 서비스가 원활하지 않습니다. 잠시 후 다시 시도해주세요.");
    }
}
