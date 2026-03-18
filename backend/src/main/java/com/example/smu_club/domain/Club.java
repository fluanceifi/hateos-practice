package com.example.smu_club.domain;

import com.example.smu_club.club.dto.ClubInfoRequest;
import com.example.smu_club.exception.custom.IllegalClubStateException;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 기본 생성자 접근 제어
@AllArgsConstructor
//인덱스의 효율성을 위해 "작업 범위 결정 조건", "이름" 순으로 인덱스 생성
//인덱스의 정렬은 왼쪽 컬럼 기준으로 진행하기 때문에 Unique한 값이 적을수록 인덱스 스킵 스캔 활용도가 높아진다.
//현재는 쓸 일이 없겠지만 추후에 특정 범위의 레코드를 검색할 때 좋은 효율이 나온다.
@Table(name = "club", indexes ={
        @Index(name = "idx_club_priority_name", columnList = "recruit_priority ASC, name ASC"),
        @Index(name = "idx_club_recruiting_closure", columnList = "recruiting_status, recruiting_end ASC")
})
public class Club {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "club_id")
    private long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String title;

    @Lob// Toast UI 에디터 내용은 길이를 예측할 수 없으므로 @Lob 추가 (TEXT 타입)
    private String description;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder.Default
    @Column(name = "recruiting_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private RecruitingStatus recruitingStatus = RecruitingStatus.CLOSED; // 기본값 명시

    @Column(name = "recruit_priority")
    private int recruitPriority;

    @Column(name = "recruiting_end")
    private LocalDate recruitingEnd;

    private String president;
    private String contact;
    private String clubRoom;

    @Column(name = "thumbnail_filekey")
    private String thumbnailFileKey;

    @OneToMany(mappedBy = "club", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ClubImage> clubImages = new ArrayList<>();

    // --- 생명주기 메서드 통합 ---

    @PrePersist
    @PreUpdate
    private void syncRecruitPriority() {
        this.recruitPriority = Objects.requireNonNullElse(this.recruitingStatus, RecruitingStatus.CLOSED).getPriority();
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    // --- 비즈니스 로직 ---

    /**
     * 모집 상태 변경
     */
    public void updateRecruitment(RecruitingStatus status) {
        if (status == RecruitingStatus.OPEN && this.recruitingStatus != RecruitingStatus.CLOSED) {
            throw new IllegalClubStateException("모집을 시작하려면 현재 닫힘 상태여야 합니다.");
        }
        this.recruitingStatus = status;
        // syncRecruitPriority는 JPA가 자동으로 호출하므로 여기서 직접 호출하지 않아도 됨 (영속성 컨텍스트 관리 하에 있을 때)
    }

    /**
     * 클럽 정보 수정
     */
    public void updateInfo(ClubInfoRequest request, String newThumbnailFileKey) {
        this.name = request.getName();
        this.title = request.getTitle();
        this.description = request.getDescription();
        this.president = request.getPresident();
        this.contact = request.getContact();
        this.clubRoom = request.getClubRoom();
        this.thumbnailFileKey = newThumbnailFileKey;
        this.recruitingEnd = request.getRecruitingEnd();
    }

    /**
     * 모집 마감
     */
    public void closeRecruitment(LocalDate closeDate) {
        if (this.recruitingStatus != RecruitingStatus.OPEN) {
            throw new IllegalClubStateException("모집 중(OPEN)인 상태에서만 마감할 수 있습니다.");
        }
        this.recruitingStatus = RecruitingStatus.CLOSED;
        this.recruitingEnd = closeDate;
    }
}