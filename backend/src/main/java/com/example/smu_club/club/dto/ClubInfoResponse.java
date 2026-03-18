package com.example.smu_club.club.dto;

import com.example.smu_club.domain.Club;
import com.example.smu_club.domain.ClubImage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

@Getter
@Builder
@AllArgsConstructor
public class ClubInfoResponse {

    private String name;
    private String title;
    private String president;
    private String contact;
    private LocalDate recruitingEnd;
    private String clubRoom;
    private String description;

    private List<ClubImagesResponseDto> clubImages;

    public static ClubInfoResponse from(Club club, Function<String, String> urlConverter) {

        List<ClubImagesResponseDto> clubImages = club.getClubImages().stream()
                .sorted(Comparator.comparingInt(ClubImage::getDisplayOrder)) // 순서 정렬
                .map(image -> new ClubImagesResponseDto(
                        image.getId(),                                // Long id
                        urlConverter.apply(image.getImageFileKey()),  // String imageUrl (키 -> URL 변환)
                        image.getDisplayOrder()                       // int orderNumber
                ))
                .toList();

        return ClubInfoResponse.builder()
                .name(club.getName())
                .title(club.getTitle())
                .president(club.getPresident())
                .contact(club.getContact())
                .recruitingEnd(club.getRecruitingEnd())
                .clubRoom(club.getClubRoom())
                .description(club.getDescription())
                .clubImages(clubImages)
                .build();
    }
}
