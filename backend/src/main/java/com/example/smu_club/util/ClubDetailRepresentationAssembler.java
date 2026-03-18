package com.example.smu_club.util;

import com.example.smu_club.club.controller.GuestClubController;
import com.example.smu_club.club.dto.ClubResponseDto;
import com.example.smu_club.domain.RecruitingStatus;
import org.springframework.hateoas.Link;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class ClubDetailRepresentationAssembler {

    public ClubResponseDto toModel(ClubResponseDto dto) {
        // self 링크
        dto.add(linkTo(methodOn(GuestClubController.class)
                .findClubById(dto.getId())).withSelfRel());

        // list 링크
        dto.add(linkTo(methodOn(GuestClubController.class)
                .findAllClubs()).withRel("list"));

        // apply 링크: 모집 중일 때만
        if (dto.getRecruitingStatus() == RecruitingStatus.OPEN) {
            dto.add(Link.of("/api/v1/member/clubs/" + dto.getId() + "/applications")
                    .withRel("apply"));
        }

        return dto;
    }
}