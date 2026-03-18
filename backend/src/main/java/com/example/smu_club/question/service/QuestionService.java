package com.example.smu_club.question.service;

import com.example.smu_club.club.repository.ClubMemberRepository;
import com.example.smu_club.domain.*;
import com.example.smu_club.exception.custom.AuthorizationException;
import com.example.smu_club.exception.custom.ClubMemberNotFoundException;
import com.example.smu_club.exception.custom.ClubNotFoundException;
import com.example.smu_club.question.dto.QuestionRequest;
import com.example.smu_club.question.dto.QuestionResponse;
import com.example.smu_club.club.repository.ClubRepository;
import com.example.smu_club.question.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final ClubRepository clubRepository;
    private final ClubMemberRepository clubMemberRepository;


    public List<QuestionResponse> findQuestionsByClubId(Long clubId, String studentId) {

        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new ClubNotFoundException("해당 동아리를 찾을 수 없습니다. ID: " + clubId));

        ClubMember clubMember = clubMemberRepository.findByClubAndMember_StudentId(club, studentId)
                .orElseThrow(() -> new ClubMemberNotFoundException("[OWNER] 해당 동아리 소속이 아니거나 존재하지 않는 회원입니다. "));

        if (clubMember.getClubRole() != ClubRole.OWNER) {
            throw new AuthorizationException("[OWNER] 해당 동아리의 질문을 볼 권한이 없습니다.");
        }

        List<Question> questions = questionRepository.findAllByClubAndQuestionContentTypeOrderByOrderNumAsc(club, QuestionContentType.TEXT);

        return questions.stream()
                .map(question -> new QuestionResponse(question.getId(), question.getOrderNum(),question.getContent()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = false)
    public void saveQuestions(Long clubId, List<QuestionRequest> questions) {

        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new ClubNotFoundException("해당 동아리를 찾을 수 없습니다." + clubId));

        questionRepository.deleteAllByClubAndQuestionContentType(club, QuestionContentType.TEXT);

        List<Question> textQuestions = new ArrayList<>();

        int currentOrderNum = 1;
        for (QuestionRequest questionRequest : questions) {
            Question question = Question.builder()
                    .club(club)
                    .content(questionRequest.getContent())
                    .orderNum(currentOrderNum++)
                    .questionContentType(QuestionContentType.TEXT)
                    .build();
            textQuestions.add(question);
        }

        questionRepository.saveAll(textQuestions);

        // 마지막엔 강제로 fileQuestion이 하나 만들어짐 - 프런트에서는 기본으로 하나 만들어짐
        questionRepository.deleteAllByClubAndQuestionContentType(club, QuestionContentType.FILE);

        Question fileQuestion = Question.builder()
                .club(club)
                .content("파일을 업로드 해주세요(선택)")
                .orderNum(currentOrderNum)
                .questionContentType(QuestionContentType.FILE)
                .build();
        questionRepository.save(fileQuestion);



    }
}
