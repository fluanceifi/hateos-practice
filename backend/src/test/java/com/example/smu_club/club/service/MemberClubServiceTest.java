package com.example.smu_club.club.service;

import com.example.smu_club.answer.repository.AnswerRepository;
import com.example.smu_club.club.repository.ClubMemberRepository;
import com.example.smu_club.club.repository.ClubRepository;
import com.example.smu_club.domain.Club;
import com.example.smu_club.domain.RecruitingStatus;
import com.example.smu_club.exception.custom.ClubNotFoundException;
import com.example.smu_club.exception.custom.ClubNotRecruitmentPeriodException;
import com.example.smu_club.exception.custom.QuestionNotFoundException;
import com.example.smu_club.member.repository.MemberRepository;
import com.example.smu_club.question.repository.QuestionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MemberClubServiceTest {
    @Mock
    private ClubRepository clubRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ClubMemberRepository clubMemberRepository;

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private AnswerRepository answerRepository;

    @InjectMocks
    private MemberClubService memberClubService;

    private final Long testClubId = 1L;
    private final String testStudentId = "202215064";

    @Test
    @DisplayName("실패: 동아리 없음")
    public void 지원서_가져오기_단위테스트_실패케이스1_ClubNotFound() {
        //given
        when(clubRepository.findById(anyLong())).thenReturn(Optional.empty()); //no club info


        //when & then
        assertThrows(ClubNotFoundException.class, () -> memberClubService.findMemberInfoWithQuestions(testClubId, testStudentId));

        verify(clubRepository, times(1)).findById(testClubId);
        verify(questionRepository, never()).findAllByClubOrderByOrderNumAsc(any());
        verify(memberRepository, never()).findByStudentId(anyString());
    }

    @Test
    @DisplayName("실패: 모집 기간 아님")
    public void 지원서_가져오기_단위테스트_실패케이스2_ClubNotRecruitmentPeriod() {
        //given
        Club mockClub = Club.builder()
                .id(testClubId)
                .name("동아리2")
                .recruitingStatus(RecruitingStatus.CLOSED)
                .build();

        //when
        when(clubRepository.findById(testClubId)).thenReturn(Optional.of(mockClub)); //목 초기화

        //then
        assertThrows(ClubNotRecruitmentPeriodException.class, () -> memberClubService.findMemberInfoWithQuestions(testClubId, testStudentId));

        verify(clubRepository, times(1)).findById(testClubId);
        verify(questionRepository, never()).findAllByClubOrderByOrderNumAsc(any());
        verify(memberRepository, never()).findByStudentId(anyString());
    }

    @Test
    @DisplayName("실패: 질문 없음")
    public void 지원서_가져오기_단위테스트_실패케이스3_QuestionNotFound() {
        //given
        Club mockClub = Club.builder()
                .id(testClubId)
                .name("동아리3")
                .recruitingStatus(RecruitingStatus.OPEN)
                .build();

        //when
        when(clubRepository.findById(testClubId)).thenReturn(Optional.of(mockClub)); //목 초기화
        when(questionRepository.findAllByClubOrderByOrderNumAsc(mockClub)).thenReturn(new ArrayList<>()); //비어있는 목 초기화

        //then
        assertThrows(QuestionNotFoundException.class, () -> memberClubService.findMemberInfoWithQuestions(testClubId, testStudentId));

        // clubRepository.findById가 1번 호출되었는지 검증
        verify(clubRepository, times(1)).findById(testClubId);
        // questionRepository.findAllByClubOrderByOrderNumAsc가 1번 호출되었는지 검증
        verify(questionRepository, times(1)).findAllByClubOrderByOrderNumAsc(mockClub);
        // 질문을 찾지 못했으므로 memberRepository는 호출되지 않아야 함
        verify(memberRepository, never()).findByStudentId(anyString());
    }


}
