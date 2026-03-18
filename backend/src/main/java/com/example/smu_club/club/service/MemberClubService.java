package com.example.smu_club.club.service;


import com.example.smu_club.answer.dto.AnswerRequestDto;
import com.example.smu_club.answer.repository.AnswerRepository;
import com.example.smu_club.club.dto.*;
import com.example.smu_club.club.repository.ClubMemberRepository;
import com.example.smu_club.club.repository.ClubRepository;
import com.example.smu_club.domain.*;
import com.example.smu_club.exception.custom.*;
import com.example.smu_club.member.repository.MemberRepository;
import com.example.smu_club.question.dto.QuestionResponse;
import com.example.smu_club.question.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.example.smu_club.domain.RecruitingStatus.OPEN;
import static java.util.stream.Collectors.toList;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberClubService {
    private final ClubRepository clubRepository;
    private final MemberRepository memberRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;

    @Transactional(readOnly = false)
    public ApplicationResponseDto saveApplication(Long clubId, String studentId, List<AnswerRequestDto> questionAndAnswer, String fileKey) {
        // 1. ClubMember 에 회원 등록 (Status 기본 값은 PENDING)
        Member user = memberRepository.findByStudentId(studentId).orElseThrow(() -> new MemberNotFoundException("student id = "+ studentId +"에 해당하는 회원을 찾을 수 없습니다."));

        Club appliedClub = clubRepository.findById(clubId).orElseThrow(() -> new ClubNotFoundException("club id = "+ clubId +"에 해당하는 동아리를 찾을 수 없습니다."));

        ClubMember clubMember = ClubMember.builder()
                .member(user)
                .club(appliedClub)
                .clubRole(ClubRole.MEMBER)
                .appliedAt(LocalDateTime.now())
                .status(ClubMemberStatus.PENDING)
                .emailStatus(EmailStatus.READY)
                .build();
        clubMemberRepository.save(clubMember);

        // 2. 지원서 답변 및 파일 저장
        List<Question> allQuestions = questionRepository.findByClubId(clubId);

        Map<Long, String> answerMap = questionAndAnswer.stream()
                .collect(Collectors.toMap(AnswerRequestDto::getQuestionId, AnswerRequestDto::getAnswerContent));

        List<Answer> answersToSave = new ArrayList<>();

        for (Question question : allQuestions) {
            Answer answer = new Answer();
            answer.setQuestion(question);
            answer.setMember(user);

            if (question.getQuestionContentType() == QuestionContentType.FILE) {
                answer.setFileKey(fileKey);
            } else {
                answer.setAnswerContent(answerMap.get(question.getId()));
            }
            answersToSave.add(answer);
        }

        answerRepository.saveAll(answersToSave);

        return new ApplicationResponseDto(questionAndAnswer, fileKey);
    }

    @Transactional(readOnly = true)
    public ApplicationFormResponseDto findMemberInfoWithQuestions(Long clubId, String studentId){
        /**
         * questions search
         */
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new ClubNotFoundException("동아리 ID: "+ clubId +" 해당 동아리를 찾을 수 없습니다."));

        if(club.getRecruitingStatus() != OPEN) throw new ClubNotRecruitmentPeriodException("동아리 ID:"+ clubId +" 해당 동아리는 모집기간이 아닙니다.");

        List<Question> questionList =
                questionRepository.findAllByClubOrderByOrderNumAsc(club);

        //Spring Data JPA의 findAllBy 같은 조회 메서드는 결과가 없을 때 null을 반환하지 않고 empty()인 상태이다.
        //if문에 null아닌 isEmpty()로 설정해야 된다.
        if(questionList.isEmpty()) throw new QuestionNotFoundException("동아리 ID: " + clubId + "의 질문을 찾을 수 없습니다.");

        List<QuestionResponse> clubQuestionListResponse =
                questionList.stream().map
                        (qr -> new QuestionResponse(
                                qr.getId(),
                                qr.getOrderNum(),
                                qr.getContent()
                        )).collect(toList());
        /**
         * Member search
         */
        Member myInfo = memberRepository.findByStudentId(studentId).orElseThrow(() -> new MemberNotFoundException("student id = "+ studentId +" is not found"));
        /**
         * Transaction to Dto
         */
        return new ApplicationFormResponseDto(
                myInfo.getId(),
                myInfo.getStudentId(),
                myInfo.getName(),
                myInfo.getDepartment(),
                myInfo.getPhoneNumber(),
                clubQuestionListResponse
        );
    }
}
