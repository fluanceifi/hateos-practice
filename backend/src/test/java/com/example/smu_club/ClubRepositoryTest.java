package com.example.smu_club;

import com.example.smu_club.club.repository.ClubMemberRepository;
import com.example.smu_club.club.repository.ClubRepository;
import com.example.smu_club.club.service.OwnerClubService;
import com.example.smu_club.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // 내장 DB 교체 안 함
public class ClubRepositoryTest {
    @Mock
    private ClubRepository clubRepository;
    @Mock
    private ClubMemberRepository clubMemberRepository;
    @InjectMocks //테스트 대상은 Inject한다.
    private OwnerClubService ownerClubService;

    @Test
    @DisplayName("보류 중인 멤버를 조회하면 상태가 PROCESSING으로 변경되고 ID 리스트를 반환한다")
    void fetchPendingAndMarkAsProcessing_Success() {
        // given
        Long clubId = 1L;
        RecruitingStatus status = RecruitingStatus.CLOSED;

        Club club = Club.builder()
                .id(clubId)
                .recruitingStatus(status)
                .build();

        // 모집 종료 및 확정 상태라고 가정 (isClosedAndConfirmed가 true를 반환하도록 설정)
        //given(club.isClosed()).willReturn(true);
        given(clubRepository.findById(clubId)).willReturn(Optional.of(club));

        ClubMember member1 = spy(ClubMember.builder().id(10L).emailStatus(EmailStatus.READY).status(ClubMemberStatus.PENDING).build());
        ClubMember member2 = spy(ClubMember.builder().id(11L).emailStatus(EmailStatus.READY).status(ClubMemberStatus.PENDING).build());
        List<ClubMember> targets = List.of(member1, member2);

        given(clubMemberRepository.findByClubAndEmailStatus(club, EmailStatus.READY)).willReturn(targets);

        // when
        List<Long> resultIds = ownerClubService.fetchPendingAndMarkAsProcessing(clubId);

        // then
        assertThat(resultIds).containsExactlyInAnyOrder(10L, 11L);
        assertThat(member1.getEmailStatus()).isEqualTo(EmailStatus.PROCESSING);
        assertThat(member2.getEmailStatus()).isEqualTo(EmailStatus.PROCESSING);

        // Dirty Checking에 의해 상태가 변경되었는지 확인
        verify(member1).setEmailStatus(EmailStatus.PROCESSING);
    }

}
