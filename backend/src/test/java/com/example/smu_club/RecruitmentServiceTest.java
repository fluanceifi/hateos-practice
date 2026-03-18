//package com.example.smu_club;
//
//import com.example.smu_club.club.repository.ClubMemberRepository;
//import com.example.smu_club.club.repository.ClubRepository;
//import com.example.smu_club.club.service.OwnerClubService;
//import com.example.smu_club.domain.*;
//import com.example.smu_club.util.RecruitmentService;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
//import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.LocalDate;
//import java.util.List;
//import java.util.Optional;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.mockito.BDDMockito.given;
//import static org.mockito.Mockito.*;
//
//@SpringBootTest
//@Transactional
//public class RecruitmentServiceTest {
//    @Autowired
//    RecruitmentService recruitmentService;
//    @Autowired
//    ClubRepository clubRepository;
//    @Autowired
//    ClubMemberRepository clubMemberRepository;
//    @Autowired
//    private OwnerClubService ownerClubService;
//
//
//
//    @Test
//    @DisplayName("모집 마감 대상 동아리 조회 테스트")
//    public void testFindEndedClubs() {
//
//        //given
//        Club expiredClub = Club.builder()
//                .name("Expired Club")
//                .recruitingStatus(com.example.smu_club.domain.RecruitingStatus.OPEN)
//                .recruitingEnd(java.time.LocalDate.now().minusDays(1))
//                .build();
//
//        Club activeClub = Club.builder()
//                .name("Active Club")
//                .recruitingStatus(com.example.smu_club.domain.RecruitingStatus.OPEN)
//                .recruitingEnd(java.time.LocalDate.now().plusDays(1))
//                .build();
//
//        clubRepository.save(expiredClub);
//        clubRepository.save(activeClub);
//
//
//        //when
//        List<RecruitmentService.ClosureTarget> endedClubs = recruitmentService.findEndedClubs(LocalDate.now());
//
//        recruitmentService.closeRecruitments(endedClubs);
//
//        //then
//
//        Club updatedExpiredClub = clubRepository.findById(expiredClub.getId()).orElseThrow();
//        Club updatedActiveClub = clubRepository.findById(activeClub.getId()).orElseThrow();
//
//        assertThat(updatedExpiredClub.getRecruitingStatus()).isEqualTo(RecruitingStatus.CLOSED);
//        assertThat(updatedActiveClub.getRecruitingStatus()).isEqualTo(RecruitingStatus.OPEN);
//    }
//
//
//}
