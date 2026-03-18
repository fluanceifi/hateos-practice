package com.example.smu_club.club.dto;

import com.example.smu_club.domain.ClubMember;
import com.example.smu_club.domain.ClubMemberStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ApplicantStatusUpdateRequest {

    @NotNull(message = "변경할 상태를 입력해주세요.")
    private ClubMemberStatus newStatus; // ACCEPTED, REJECTED 중 하나

}
