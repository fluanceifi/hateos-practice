package com.example.smu_club.util.expiredclubmember.service;

import com.example.smu_club.club.repository.ClubMemberRepository;
import com.example.smu_club.domain.ClubMember;
import com.example.smu_club.util.expiredclubmember.repository.BatchClubMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BatchClubMemberService {
    private final BatchClubMemberRepository batchClubMemberRepository;

    @Transactional
    public List<ClubMember> findExpiredClubMembers() {
        //1. 모집 종료된지 1달이 지난 동아리 회원 조회
        // 오늘 날짜에서 1달 이전 날짜 >= 동아리 모집 종료일 이라면, 삭제 대상으로 간주한다.
        return batchClubMemberRepository.findExpiredClubMembers(LocalDate.now().minusMonths(1));
    }


    public int cleanupExpiredClubMembers(List<ClubMember> expiredClubMembers) {
        //2. 만료된 동아리 회원 삭제 (배치 삭제)
        return batchClubMemberRepository.deleteAllInBatchWithCount(expiredClubMembers);
    }
}
