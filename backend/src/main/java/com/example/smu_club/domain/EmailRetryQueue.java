package com.example.smu_club.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "email_retry_queue", indexes = @Index(name = "idx_retry_schedule", columnList = "nextRetryDate"))

public class EmailRetryQueue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long clubMemberId;

    private String email;

    private String subject;

    private String body;

    private int retryCount;

    private LocalDateTime nextRetryDate;

    @Builder
    public EmailRetryQueue(Long clubMemberId, String email, String subject, String body){
        this.clubMemberId = clubMemberId;
        this.email = email;
        this.subject = subject;
        this.body = body;
        this.retryCount = 0;
        this.nextRetryDate = LocalDateTime.now().plusMinutes(1); // 처음에는 1분 후에 재시도
    }

    // 지수 백오프: 2^retryCount 분 후에 재시도
    public void backoff(){
        this.retryCount++;
        long wait = (long) Math.pow(2, this.retryCount);
        this.nextRetryDate = LocalDateTime.now().plusMinutes(wait);
    }
}
