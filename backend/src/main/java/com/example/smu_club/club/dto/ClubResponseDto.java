package com.example.smu_club.club.dto;

import com.example.smu_club.domain.Club;
import com.example.smu_club.domain.ClubImage;
import com.example.smu_club.domain.RecruitingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;


import static java.util.stream.Collectors.toList;

@Builder
@Getter
public class ClubResponseDto extends RepresentationModel<ClubResponseDto> {
    private Long id;
    private String name;
    private LocalDateTime createdAt;
    private RecruitingStatus recruitingStatus;
    private LocalDate recruitingEnd;
    private String president;
    private String title;
    private String contact;
    private String clubRoom;
    private String thumbnailUrl;
    //toast UI
    private String description;
    //club Images
    private List<ClubImagesResponseDto> clubImages;

    public static ClubResponseDto from(Club findClub, Function<String, String> createFinalOciUrl) {
        List<ClubImagesResponseDto> clubImages = findClub.getClubImages().stream()
                .sorted(Comparator.comparingInt(ClubImage::getDisplayOrder))
                .map(ci -> new ClubImagesResponseDto(
                        ci.getId(),
                        createFinalOciUrl.apply(ci.getImageFileKey()),
                        ci.getDisplayOrder()
                )).collect(toList());

        return ClubResponseDto.builder()
                .id(findClub.getId())
                .name(findClub.getName())
                .createdAt(findClub.getCreatedAt())
                .recruitingStatus(findClub.getRecruitingStatus())
                .recruitingEnd(findClub.getRecruitingEnd())
                .president(findClub.getPresident())
                .title(findClub.getTitle())
                .contact(findClub.getContact())
                .clubRoom(findClub.getClubRoom())
                .thumbnailUrl(createFinalOciUrl.apply(findClub.getThumbnailFileKey()))
                .description(findClub.getDescription())
                .clubImages(clubImages)
                .build();

    }
}
