package com.example.smu_club.util.scheduler;


import com.example.smu_club.util.discord.annotation.DiscordAlert;
import com.example.smu_club.util.RecruitmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class RecruitmentAutoClosureScheduler {
    private final RecruitmentService recruitmentService;
    private final static int BATCH_SIZE = 10;

    // 매일 자정(00:00)에 자동으로 모집 상태를 종료하는 스케줄러
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    @DiscordAlert("동아리 모집 자동 마감 스케줄러")
    public void closeExpiredRecruitments() {
        log.info("[스케줄러] 모집상태 자동 마감 스케줄러 시작");

        try {
            // 1단계: 처리 대상 조회
            List<RecruitmentService.ClosureTarget> deadLineList = recruitmentService.findEndedClubs(LocalDate.now());
            int totalProcessed = 0;

            if (deadLineList.isEmpty()) {
                log.info("마감될 동아리가 없습니다.");
                return;
            }

            log.info("[스케줄러] 총 {}개의 동아리 마감 처리 시도", deadLineList.size());

            // 2단계: Chunk 단위로 분할 처리
            for (int start = 0; start < deadLineList.size(); start += BATCH_SIZE) {
                int end = Math.min(start + BATCH_SIZE, deadLineList.size());
                List<RecruitmentService.ClosureTarget> chunk = deadLineList.subList(start, end);

                totalProcessed += recruitmentService.closeRecruitments(chunk);

                log.info("[스케줄러] {} ~ {}번째 동아리 마감 처리 완료 ({}건)",
                        start + 1, end, totalProcessed);
            }

            // 3단계: 결과 검증
            if (totalProcessed != deadLineList.size()) {
                log.warn("[스케줄러] 일부 동아리 처리 누락 (대상: {}, 실제 처리: {}) - 이미 삭제되었을 수 있습니다.",
                        deadLineList.size(), totalProcessed);
            } else {
                log.info("[스케줄러] 모든 동아리 마감 처리 완료 ({}건)", totalProcessed);
            }


            log.info("[스케줄러] 모집상태 자동 마감 스케줄러 종료");

        } catch (Exception e) {
            log.error("[스케줄러] 모집상태 자동 마감 스케줄러 중 오류 발생: {}", e.getMessage(), e);

        }
    }
}
