package com.example.smu_club.member.repository;

import com.example.smu_club.domain.Member;
import com.example.smu_club.member.dto.UpdateMyInfoResponseDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByStudentId(String studentId);

    Optional<Member> findByRefreshToken(String refreshToken);

    @Query("SELECT new com.example.smu_club.member.dto.UpdateMyInfoResponseDto(m.id, m.email, m.phoneNumber) " +
            "FROM Member m " +
            "where m.studentId = :studentId")
    Optional<UpdateMyInfoResponseDto> findEditMyInfoByStudentId(String studentId);
}
