package com.example.smu_club.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@MappedSuperclass // 상속받는 엔티티들이 이 필드들을 컬럼으로 인식하게 함
@EntityListeners(AuditingEntityListener.class) // JPA Auditing 기능 활성화
public abstract class BaseTimeEntity {

    @CreatedDate
    @Column(name = "created_at", updatable = false) //생성 후 변경 불가
    private Long createdAt; // 생성 시간 (타임스탬프)

    @LastModifiedDate
    @Column(name = "updated_at")
    private Long updatedAt; // 수정 시간 (타임스탬프)


}
