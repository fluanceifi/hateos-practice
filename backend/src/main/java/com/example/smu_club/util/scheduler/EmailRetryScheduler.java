package com.example.smu_club.util.scheduler;

import com.example.smu_club.domain.EmailRetryQueue;
import com.example.smu_club.email.repository.EmailRetryQueueRepository;

import com.example.smu_club.email.service.EmailRetryService;
import com.example.smu_club.util.discord.annotation.DiscordAlert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailRetryScheduler {

    private final EmailRetryQueueRepository emailRetryQueueRepository;
    private final EmailRetryService emailRetryService;

    @Scheduled(cron = "0 * * * * *") // 매 분마다 실행
    @DiscordAlert("이메일 재전송 스케줄러")
    public void retryFailedEmails() {
        // 1. 읽기 전용 트랜잭션이 아니어도 됨 (조회만 함)
        Pageable limit = PageRequest.of(0, 100);
        List<EmailRetryQueue> tasks = emailRetryQueueRepository.findAllByNextRetryDateBefore(LocalDateTime.now(), limit);

        if (tasks.isEmpty()) {
            return;
        }

        log.info("재전송 스케줄러 실행 대상 {}건", tasks.size());

        // 2. 반복문 돌면서 '건별로' 트랜잭션 처리
        for (EmailRetryQueue task : tasks) {
            try {
                // processSingleTask 메서드가 하나의 트랜잭션 단위가 됨
                emailRetryService.processSingleTask(task);
            } catch (Exception e) {
                // processSingleTask 내부에서 잡지 못한 예기치 못한 시스템 에러 로깅
                log.error("스케줄러 루프 중 알 수 없는 에러 발생 ID: {}", task.getId(), e);
            }
        }
    }


}