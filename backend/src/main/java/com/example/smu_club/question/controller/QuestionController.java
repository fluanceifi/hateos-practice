package com.example.smu_club.question.controller;

import com.example.smu_club.common.ApiResponseDto;
import com.example.smu_club.domain.Question;
import com.example.smu_club.question.dto.QuestionRequest;
import com.example.smu_club.question.dto.QuestionResponse;
import com.example.smu_club.question.service.QuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/owner/clubs")
public class QuestionController {

    private final QuestionService questionService;

    @GetMapping("/{clubId}/questions")
    public ResponseEntity<ApiResponseDto<List<QuestionResponse>>> getClubQuestions(
            @PathVariable Long clubId,
            @AuthenticationPrincipal User user) {
        List<QuestionResponse> questions = questionService.findQuestionsByClubId(clubId, user.getUsername());

        ApiResponseDto<List<QuestionResponse>> response = ApiResponseDto.success(questions, "질문조회에 성공했습니다.");

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{clubId}/questions")
    public ResponseEntity<ApiResponseDto<Void>> saveClubQuestions(
            @PathVariable Long clubId,
            @RequestBody List<QuestionRequest> questions) {

        questionService.saveQuestions(clubId, questions);
        ApiResponseDto<Void> response = ApiResponseDto.success("[OWNER] 질문저장에 성공했습니다");

        return ResponseEntity.ok(response);
    }
}
