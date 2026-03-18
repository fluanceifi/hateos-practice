package com.example.smu_club.util;

import com.example.smu_club.club.repository.ClubRepository;
import com.example.smu_club.domain.Club;
import com.example.smu_club.domain.RecruitingStatus;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecruitmentService {
    private final ClubRepository clubRepository;

    @Transactional(readOnly = true)
    public List<ClosureTarget> findEndedClubs(LocalDate now) {
        List<Club> deadLineList = clubRepository.findDeadLineClubs(RecruitingStatus.OPEN, now);

        return deadLineList.stream()
                .map(ClosureTarget::from)
                .toList();
    }

    //호출될 때마다 새 트랜잭션 생성
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int closeRecruitments(List<ClosureTarget> targets) {

        //이미 다 open 이지만, 혹시 모르니 안전하게 filter 처리
        List<Long> ids = targets.stream()
                .filter(target -> target.previousStatus() == RecruitingStatus.OPEN)
                .map(ClosureTarget::clubId)
                .toList();

        //빈 리스트로 쿼리 날리 에러 가능성 있음.
        if (ids.isEmpty()) {
            return 0;
        }


        return clubRepository.updateRecruitingStatusBatch(
                ids,
                RecruitingStatus.CLOSED
        );
    }

    //레코드: 불변 객체를 간결하게 표현하는 자바의 특별한 클래스
    @Builder
    public record ClosureTarget(long clubId, RecruitingStatus previousStatus) {

        public static ClosureTarget from(Club club) {
            return ClosureTarget.builder()
                    .clubId(club.getId())
                    .previousStatus(club.getRecruitingStatus())
                    .build();
        }

    }
}
