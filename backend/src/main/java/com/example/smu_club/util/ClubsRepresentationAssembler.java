package com.example.smu_club.util;

import com.example.smu_club.club.controller.GuestClubController;
import com.example.smu_club.club.dto.ClubsResponseDto;
import com.example.smu_club.domain.RecruitingStatus;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class ClubsRepresentationAssembler
        implements RepresentationModelAssembler<ClubsResponseDto, ClubsResponseDto> {

    @Override
    public ClubsResponseDto toModel(ClubsResponseDto dto) {
        // self 링크: 항상 포함
        dto.add(linkTo(methodOn(GuestClubController.class)
                .findClubById(dto.getId())).withSelfRel());

        // list 링크: 항상 포함
        dto.add(linkTo(methodOn(GuestClubController.class)
                .findAllClubs()).withRel("list"));

        // OPEN일 때: 로그인 여부에 따라 apply 또는 login 링크
        if (dto.getRecruitingStatus() == RecruitingStatus.OPEN) {
            if (isLoggedIn()) {
                dto.add(Link.of("/api/v1/member/clubs/" + dto.getId() + "/applications")
                        .withRel("apply"));
            } else {
                dto.add(Link.of("/api/v1/public/auth/login")
                        .withRel("login"));
            }
        }

        return dto;
    }

    private boolean isLoggedIn() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
    }
}