package com.example.smu_club.email.service;


import com.example.smu_club.club.repository.ClubMemberRepository;
import com.example.smu_club.domain.EmailRetryQueue;
import com.example.smu_club.email.repository.EmailRetryQueueRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static com.example.smu_club.domain.EmailStatus.COMPLETE;
import static com.example.smu_club.domain.EmailStatus.GIVE_UP;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailRetryService {
    private final ClubMemberRepository clubMemberRepository;
    private final EmailRetryQueueRepository emailRetryQueueRepository;
    private final JavaMailSender javaMailSender;

    // 트랜잭션 분리를 위해 메서드 추출 (REQUIRES_NEW: 무조건 새로운 트랜잭션 생성)
    // 각 이메일 재전송 작업마다 별도의 트랜잭션으로 처리 (독립적 커밋/최소 범위 롤백)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processSingleTask(EmailRetryQueue task) {
        Long clubMemberId = task.getClubMemberId();

        try {
            // 3. 재전송 시도
            resendEmail(task);

            // 4. 성공 시: 큐 삭제 + 상태 업데이트 (하나의 트랜잭션으로 묶임)
            handleSuccess(task, clubMemberId);

            log.info("이메일 재전송 성공. clubMemberId: {}", clubMemberId);
        } catch (Exception e) {
            // 5. 실패 시: 재시도 카운트 증가 + 다음 재시도 시간 업데이트 (하나의 트랜잭션으로 묶임)
            handleFailure(task, clubMemberId, e);
        }
    }

    private void handleSuccess(EmailRetryQueue task, Long clubMemberId) {
        emailRetryQueueRepository.delete(task);
        clubMemberRepository.updateEmailStatus(clubMemberId, COMPLETE);
    }

    private void handleFailure(EmailRetryQueue task, Long clubMemberId, Exception e) {
        int currentRetryCount = task.getRetryCount();
        String email = task.getEmail();

        log.warn("이메일 재전송 실패 (시도 {}회) - clubMemberId: {}, error: {}",
                currentRetryCount + 1, clubMemberId, e.getMessage());

        if (currentRetryCount >= 5) {
            // 5회 초과시 포기 (GIVE_UP)
            emailRetryQueueRepository.delete(task);
            clubMemberRepository.updateEmailStatus(clubMemberId, GIVE_UP);
            log.error("최대 재시도 횟수 초과. 포기 처리: {}", email);
        } else {
            // 백오프 적용 후 업데이트
            task.backoff(); // 엔티티 내부 값 변경
            emailRetryQueueRepository.save(task); // 변경사항 저장
        }
    }

    private void resendEmail(EmailRetryQueue task) throws Exception {
        MimeMessage message = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "utf-8");
        helper.setFrom("no-reply@smuclub.com", "스뮤클럽");
        helper.setTo(task.getEmail());
        helper.setSubject(task.getSubject());
        helper.setText(task.getBody(), false);
        javaMailSender.send(message);
    }
}
