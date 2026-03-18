package com.example.smu_club.util.expiredclubmember.repository;

import com.example.smu_club.domain.ClubMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

public interface BatchClubMemberRepository extends JpaRepository<ClubMember, Long> {
    // n+1 문제를 해결하기 위한 쿼리는 아니므로, JOIN(교집합)을 사용한다.
    @Query("SELECT cm FROM ClubMember cm JOIN cm.club c " +
            "WHERE cm.clubRole = com.example.smu_club.domain.ClubRole.MEMBER " +
            "AND c.recruitingEnd <= :expirationDate")
    List<ClubMember> findExpiredClubMembers(LocalDate expirationDate);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM ClubMember cm WHERE cm IN :expiredClubMembers")
    int deleteAllInBatchWithCount(List<ClubMember> expiredClubMembers);

}
