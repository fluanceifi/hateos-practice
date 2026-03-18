package com.example.smu_club.club.service;

import com.example.smu_club.club.repository.ClubMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ClubMemberRelationService {
    private final ClubMemberRepository clubMemberRepository;

    public Long countClubMembers(Long clubId){
        return clubMemberRepository.countByClubId(clubId);
    }
}
