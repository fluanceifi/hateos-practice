package com.example.smu_club.club.dto;

import com.example.smu_club.domain.RecruitingStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hibernate.boot.jaxb.hbm.internal.RepresentationModeConverter;
import org.springframework.hateoas.RepresentationModel;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ClubsResponseDto extends RepresentationModel<ClubsResponseDto> {
    private Long id;
    private String name;
    private String title;
    private RecruitingStatus recruitingStatus;
    private LocalDateTime createdAt;
    private String thumbnailUrl;
}
