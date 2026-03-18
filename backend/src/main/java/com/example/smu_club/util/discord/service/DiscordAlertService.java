package com.example.smu_club.util.discord.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class DiscordAlertService {

    @Value("${discord.webhook.url}")
    private String webhookUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    // 알림 전송 실패가 메인 로직에 영향을 주지 않도록 별도 쓰레드에서 실행
    @Async
    public void sendErrorAlert(String message){
        try{
            //디스코드 규격에 맞춘 Map 생성
            Map<String, Object> body = Map.of(
                    "content", "**[SMU-CLUB 서버 스케줄러 에러 림람]**",
                    "embeds", List.of(Map.of(
                                    "title", "서버 스케줄러 에러 발생",
                                    "description", message,
                                    "color", 16711680 // 빨간색
                            ))
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            restTemplate.postForEntity(webhookUrl, entity, String.class);

        } catch(Exception e){
            log.error("디스코드 알림 전송 실패: {}, 이것까지 터지면 무슨 일이 있었는지 파악 못하는  상황임. ", e.getMessage());
        }
    }
}
