package com.example.smu_club.club.dto;

import com.example.smu_club.domain.Club;
import com.example.smu_club.domain.ClubImage;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClubImagesResponseDto {
    private Long id;
    private String imageUrl;
    private int orderNumber;

}
