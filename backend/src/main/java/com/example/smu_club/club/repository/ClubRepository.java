package com.example.smu_club.club.repository;

import com.example.smu_club.domain.Club;
import com.example.smu_club.domain.RecruitingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ClubRepository extends JpaRepository<Club, Long> {


    @Query("SELECT c FROM Club c ORDER BY c.recruitPriority ASC, c.name ASC")
    List<Club> findAllSortedByRecruitment();

    boolean existsByName(String name);

    @Query("SELECT c FROM Club c LEFT JOIN FETCH c.clubImages WHERE c.id = :clubId")
    Optional<Club> findByIdWithClubImages(Long clubId);

    @Query("SELECT c FROM Club c " +
            "WHERE c.recruitingStatus = :status " +
            "AND c.recruitingEnd < :now")
    List<Club> findDeadLineClubs(
            @Param("status") RecruitingStatus status,
            @Param("now") LocalDate now
    );

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Club c SET c.recruitingStatus = :recruitingStatus WHERE c.id IN :list")
    int updateRecruitingStatusBatch(List<Long> list, RecruitingStatus recruitingStatus);
}

