//package com.example.smu_club;
//
//import com.example.smu_club.club.repository.ClubMemberRepository;
//import com.example.smu_club.club.repository.ClubRepository;
//import com.example.smu_club.domain.*;
//import com.example.smu_club.member.repository.MemberRepository;
//import com.example.smu_club.util.expiredclubmember.repository.BatchClubMemberRepository;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.LocalDate;
//import java.util.List;
//
//import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
//
//@SpringBootTest
//@Transactional
//public class ClubMemberRepositoryTest {
//    @Autowired
//    private BatchClubMemberRepository batchClubMemberRepository;
//
//    @Autowired
//    private ClubMemberRepository clubMemberRepository;
//
//    @Autowired
//    private ClubRepository clubRepository;
//
//    @Autowired
//    MemberRepository memberRepository;
//
//    @BeforeEach
//    void setUp() {
//        clubMemberRepository.deleteAllInBatch();
//    }
//
//    @Test
//    @DisplayName("1달이 지나 만료된 동아리 회원 조회 테스트")
//    public void testFindExpiredClubMembers() {
//        //Given
//        String randomStr = String.valueOf(System.currentTimeMillis());
//
//        String uniqueId = System.currentTimeMillis() + "";
//
//        Member member = memberRepository.save(Member.builder()
//                .studentId("20230001"+ uniqueId)
//                .name("Test1")
//                .email("asdfasasdfdf@gmail.com")
//                .department("Computer Science")
//                .phoneNumber(uniqueId)
//                .role(Role.OWNER)
//                .createdAt(java.time.LocalDateTime.now())
//                .refreshToken("sample_refresh_token")
//                .build()
//        );
//
//        Club club = clubRepository.save(Club.builder()
//                .name(randomStr)
//                .title("A Club for Testing")
//                .description("This club is created for testing purposes.")
//                .createdAt(java.time.LocalDateTime.now())
//                .recruitPriority(1)
//                .recruitingEnd(java.time.LocalDate.now().minusMonths(2))
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
//                .clubRole(ClubRole.MEMBER) //OWNER가 되면 isEqualTo(0)이 되야함.
//                .appliedAt(java.time.LocalDateTime.now().minusMonths(2))
//                .status(ClubMemberStatus.ACCEPTED)
//                .emailStatus(EmailStatus.PROCESSING)
//                .memo("Initial memo")
//                .retryCount(0)
//                .build()
//        );
//
//        //When & Then
//        List<ClubMember> result = batchClubMemberRepository.findExpiredClubMembers(LocalDate.now().minusMonths(1));
//        assertThat(result.size()).isEqualTo(1);
//
//        int num = batchClubMemberRepository.deleteAllInBatchWithCount(result);
//        assertThat(num).isEqualTo(1);
//
//    }
//
//
//}
