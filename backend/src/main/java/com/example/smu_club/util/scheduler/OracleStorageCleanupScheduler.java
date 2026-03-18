package com.example.smu_club.util.scheduler;

import com.example.smu_club.answer.repository.AnswerRepository;
import com.example.smu_club.club.repository.ClubImageRepository;
import com.example.smu_club.util.discord.annotation.DiscordAlert;
import com.example.smu_club.util.oci.OciStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class OracleStorageCleanupScheduler {
    private final OciStorageService ociStorageService;
    private final AnswerRepository answerRepository;
    private final ClubImageRepository clubImageRepository;

    @Scheduled(cron = "0 0 3 * * *") // 매일 새벽 3시에 실행
    @DiscordAlert("Oracle Cloud Storage 고아 파일 정리 스케줄러")
    public void cleanUpOrphanFiles() {
        /**
         * 1. Oracle Cloud Storage에 생성된지 24시간 지난 파일 목록 조회. (24시간 지난 파일인데 DB에 없다면 빼박 고아 파일)
         * 2. DB(Answer 테이블)에 존재하는 fileKey 목록 조회. (유효한 파일 키)
         * 3. 데이터 비교 알고리즘 적용:
         *   - DB 조회 결과를 HashSet으로 변환하여 탐색 속도를 O(1)로 최적화.
         *   - Oracle Cloud Storage에서 DB Set이 존재하지 않을 경우 삭제 대상으로 간주.
         * 4. 삭제 대상 파일들을 리스트로  Oracle Cloud Storage에서 삭제.
         */

        List<String> ociFiles = ociStorageService.getOldFileKeys(24);

        if (ociFiles.isEmpty()) {
            log.info("삭제할 오래된 파일이 없습니다.");
            return;
        }

        List<String> dbFileKeys = new ArrayList<>();
        dbFileKeys.addAll(answerRepository.findAllFileKeys());
        dbFileKeys.addAll(clubImageRepository.findAllFileKeys());

        Set<String> validKeys = new HashSet<>(dbFileKeys);

        int deleteCount = 0;
        List<String> deleteKeyList = new ArrayList<>();
        for(String key : ociFiles) {
            if(!validKeys.contains(key)) {
                deleteKeyList.add(key);
                deleteCount++;
            }
        }

        if(!deleteKeyList.isEmpty()) {
            ociStorageService.deleteUrls(deleteKeyList);
            log.info("==고아 파일 삭제 완료: {}개 파일 삭제==", deleteCount);
        } else {
            log.info("==삭제할 파일이 없습니다==");
        }
    }
}
