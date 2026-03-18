//package com.example.smu_club;
//
//import com.example.smu_club.answer.repository.AnswerRepository;
//import com.example.smu_club.club.repository.ClubMemberRepository;
//import com.example.smu_club.club.repository.ClubRepository;
//import com.example.smu_club.club.service.OwnerClubService;
//import com.example.smu_club.domain.*;
//import com.example.smu_club.member.repository.MemberRepository;
//import com.example.smu_club.question.repository.QuestionRepository;
//import jakarta.persistence.EntityManager;
//import org.junit.jupiter.api.AfterEach;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//
//import java.util.List;
//import java.util.concurrent.TimeUnit;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.awaitility.Awaitility.await;
//
//@SpringBootTest
//public class OwnerClubServiceAsyncTest {
//    @Autowired
//    private OwnerClubService ownerClubService;
//    @Autowired
//    private ClubMemberRepository clubMemberRepository;
//    @Autowired
//    private ClubRepository clubRepository;
//    @Autowired
//    private MemberRepository memberRepository;
//    @Autowired
//    private QuestionRepository questionRepository;
//    @Autowired
//    private AnswerRepository answerRepository;
//
//    @AfterEach
//    void tearDown() {
//        // 자식 테이블 먼저 삭제 (외래 키 제약 조건 방지)
//        answerRepository.deleteAllInBatch();
//        questionRepository.deleteAllInBatch();
//        clubMemberRepository.deleteAllInBatch();
//
//        // 그 다음 부모 테이블 삭제
//        clubRepository.deleteAllInBatch();
//        memberRepository.deleteAllInBatch();
//    }
//
//    @Test
//    @DisplayName("비동기 이메일 발송 메서드가 정상적으로 호출되는지 확인")
//    void testAsyncEmailDispatch() {
//        //given [실제 DB에 데이터 삽입]
//        String randomStr = String.valueOf(System.currentTimeMillis());
//        String uniqueId = System.currentTimeMillis() + "";
//
//
//        Member member = memberRepository.save(Member.builder()
//                .studentId("20230001"+ uniqueId)
//                .name("Test1")
//                .email("dbtmdwns990203@gmail.com")
//                .department("Computer Science")
//                .phoneNumber(uniqueId)
//                .role(Role.MEMBER)
//                .createdAt(java.time.LocalDateTime.now())
//                .refreshToken("sample_refresh_token")
//                .build()
//        );
//
//
//        Club club = clubRepository.save(Club.builder()
//                .name(randomStr)
//                .title("A Club for Testing")
//                .description("This club is created for testing purposes.")
//                .createdAt(java.time.LocalDateTime.now())
//                .recruitPriority(1)
//                .recruitingEnd(java.time.LocalDate.now().minusDays(1))
//                .president("Test President")
//                .contact("010-0000-0000")
//                .clubRoom("Room 101")
//                .recruitingStatus(RecruitingStatus.CLOSED)
//                .thumbnailFileKey("http://example.com/thumbnail.jpg")
//                .build()
//        );
//
//        ClubMember clubMember = clubMemberRepository.save(ClubMember.builder()
//                .member(member)
//                .club(club)
//                .clubRole(ClubRole.MEMBER)
//                .appliedAt(java.time.LocalDateTime.now().minusDays(2))
//                .status(ClubMemberStatus.ACCEPTED)
//                .emailStatus(EmailStatus.PROCESSING)
//                .memo("Initial memo")
//                .retryCount(0)
//                .build()
//        );
//
//
//        List<Long> clubMemberIds = List.of(clubMember.getId());
//
//        //when
//        ownerClubService.sendEmailsAsync(club.getId(), clubMemberIds);
//
//        //then Awaitility로 상태 변경 대기
//        await()
//                .atMost(5, TimeUnit.SECONDS)//최대 5초간 대기
//                .pollInterval(500, TimeUnit.MILLISECONDS) // 0.5초마다 상태 확인
//                .untilAsserted(() -> { //검증 로직
//                    // 이메일 발송 후 상태 검증
//                    ClubMember cm = clubMemberRepository.findById(clubMember.getId()).orElseThrow();
//                    assertThat(cm.getEmailStatus()).isEqualTo(EmailStatus.COMPLETE);
//                });
//
//    }
//
//}
