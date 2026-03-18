package com.example.smu_club.club.controller;

import com.example.smu_club.club.dto.ClubResponseDto;
import com.example.smu_club.club.dto.ClubsResponseDto;
import com.example.smu_club.club.service.GuestClubService;
import com.example.smu_club.common.ApiResponseDto;
import com.example.smu_club.domain.RecruitingStatus;
import com.example.smu_club.util.ClubsRepresentationAssembler;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.Link;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;


/**
 * @author sjy
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/public")
public class GuestClubController {

    private final GuestClubService guestClubService;
    private final ClubsRepresentationAssembler clubsAssembler;

    @GetMapping("/clubs")
    public ResponseEntity<ApiResponseDto<CollectionModel<ClubsResponseDto>>> findAllClubs() {
        List<ClubsResponseDto> clubs = guestClubService.findAllClubs();

        // Assembler로 각 DTO에 링크 추가
        List<ClubsResponseDto> withLinks = clubs.stream()
                .map(clubsAssembler::toModel)
                .toList();

        // CollectionModel로 감싸서 컬렉션 자체에도 self 링크 추가
        CollectionModel<ClubsResponseDto> result = CollectionModel.of(
                withLinks,
                linkTo(methodOn(GuestClubController.class).findAllClubs()).withSelfRel()
        );

        return ResponseEntity.ok(ApiResponseDto.success(result, "전체 클럽 목록 조회 성공 [메인페이지]"));
    }


    @GetMapping("/clubs/{clubId}")
    public ResponseEntity<ApiResponseDto<ClubResponseDto>> findClubById(@PathVariable Long clubId){
        ClubResponseDto club = guestClubService.findClubById(clubId);

        // self, list 링크: 항상 포함
        club.add(linkTo(methodOn(GuestClubController.class).findClubById(clubId)).withSelfRel());
        club.add(linkTo(methodOn(GuestClubController.class).findAllClubs()).withRel("list"));

        // OPEN일 때: 로그인 여부에 따라 apply 또는 login 링크
        if (club.getRecruitingStatus() == RecruitingStatus.OPEN) {
            if (isLoggedIn()) {
                club.add(Link.of("/api/v1/member/clubs/" + clubId + "/applications").withRel("apply"));
            } else {
                club.add(Link.of("/api/v1/public/auth/login").withRel("login"));
            }
        }

        return ResponseEntity.ok(ApiResponseDto.success(club, "클럽 상세 정보 조회 성공"));
    }

    private boolean isLoggedIn() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
    }

}
