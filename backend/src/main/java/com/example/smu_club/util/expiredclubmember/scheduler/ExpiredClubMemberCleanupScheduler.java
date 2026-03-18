package com.example.smu_club.util.expiredclubmember.scheduler;

import com.example.smu_club.util.expiredclubmember.service.BatchClubMemberService;
import com.example.smu_club.domain.ClubMember;
import com.example.smu_club.util.discord.annotation.DiscordAlert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
class ExpiredClubMemberCleanupScheduler {

    private final BatchClubMemberService batchClubMemberService;

    @Scheduled(cron = "* 10 0 * * *", zone = "Asia/Seoul") // 매일 새벽 12시 10분에 실행
    @DiscordAlert("만료된 동아리 회원 정리 스케줄러")
    public void cleanupExpiredClubMembers() {
        //1. 모집 종료된 이후 1달이 지난 동아리 조회
        log.info("[스케줄러] 만료된 동아리 회원 정리 스케줄러 시작");

        try {
            List<ClubMember> expiredClubMembers = batchClubMemberService.findExpiredClubMembers();

            if (expiredClubMembers.isEmpty()) {
                log.info("만료된 동아리 회원이 없습니다.");
                return;
            }
            log.info("[스케줄러] 총 {}명의 만료된 동아리 회원 정리 처리 시도", expiredClubMembers.size());

            int result = batchClubMemberService.cleanupExpiredClubMembers(expiredClubMembers);
            if (result != expiredClubMembers.size()) {
                log.warn("[스케줄러] 일부 동아리 회원 정리 누락 (대상: {}, 실제 처리: {}) - 누군가 합/불 처리를 하고 있을 수도 있습니다.",
                        expiredClubMembers.size(), result);
            } else {
                log.info("[스케줄러] 모든 만료된 동아리 회원 정리 처리 완료 ({}건)", result);
            }
            log.info("[스케줄러] 만료된 동아리 회원 정리 스케줄러 종료");

        } catch (Exception e) {
            log.error("[스케줄러] 만료된 동아리 회원 정리 스케줄러 중 오류 발생: {}", e.getMessage(), e);
            throw e; //진짜 문제를 캐치하기 위해 Custom Exception 으로 감싸지 않는다.
        }
    }

}

