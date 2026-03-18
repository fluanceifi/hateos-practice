package com.example.smu_club.club.service;


import com.example.smu_club.club.dto.ClubImagesResponseDto;
import com.example.smu_club.club.dto.ClubResponseDto;
import com.example.smu_club.club.dto.ClubsResponseDto;
import com.example.smu_club.club.repository.ClubRepository;
import com.example.smu_club.domain.Club;
import com.example.smu_club.domain.ClubImage;
import com.example.smu_club.exception.custom.ClubNotFoundException;
import com.example.smu_club.exception.custom.ClubsNotFoundException;
import com.example.smu_club.util.oci.OciStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

import static java.util.stream.Collectors.toList;

@Service
@RequiredArgsConstructor
public class GuestClubService {

    private final ClubRepository clubRepository;
    private final OciStorageService ociStorageService;


    @Transactional
    public List<ClubsResponseDto> findAllClubs() {

        List<Club> findClubs = clubRepository.findAllSortedByRecruitment();
        if (findClubs.isEmpty())
            throw new ClubsNotFoundException("등록된 클럽이 하나도 없습니다.");


        return findClubs.stream()
                .map(c -> {
                    String thumbnailUrl = ociStorageService.createFinalOciUrl(c.getThumbnailFileKey());

                    return new ClubsResponseDto(
                            c.getId(),
                            c.getName(),
                            c.getTitle(),
                            c.getRecruitingStatus(),
                            c.getCreatedAt(),
                            thumbnailUrl
                    );
                })
                .collect(toList());
    }




    @Transactional
    public ClubResponseDto findClubById(Long clubId){
        Club findClub = clubRepository.findByIdWithClubImages(clubId)
                    .orElseThrow(() -> new ClubNotFoundException("clubId: "+ clubId +" 를 찾지 못했습니다."));

        return  ClubResponseDto.from(findClub, ociStorageService::createFinalOciUrl);
    }
}
