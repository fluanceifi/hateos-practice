package com.example.smu_club.email.repository;

import com.example.smu_club.domain.EmailRetryQueue;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EmailRetryQueueRepository extends JpaRepository<EmailRetryQueue, Long> {

    @Query("SELECT e FROM EmailRetryQueue e WHERE e.nextRetryDate <= :now ORDER BY e.nextRetryDate ASC")
    List<EmailRetryQueue> findAllByNextRetryDateBefore(LocalDateTime now, Pageable limit); //limit은 매개변수에 존재 시, 알아서 적용됨.

}
