package com.example.smu_club.club.controller;


import com.example.smu_club.answer.dto.AnswerRequestDto;
import com.example.smu_club.club.dto.ApplicationFormResponseDto;
import com.example.smu_club.club.dto.ApplicationRequestDto;
import com.example.smu_club.club.dto.ApplicationResponseDto;
import com.example.smu_club.club.dto.UploadUrlRequest;
import com.example.smu_club.club.service.MemberClubService;
import com.example.smu_club.common.ApiResponseDto;
import com.example.smu_club.common.file.FileUploadService;
import com.example.smu_club.util.PreSignedUrlResponse;
import com.example.smu_club.util.oci.OciStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;


/**
 * @author sjy
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/member/clubs")
public class MemberClubController {

    private final MemberClubService memberClubService;
    private final FileUploadService fileUploadService;

    @GetMapping("/{clubId}/apply")
    public ResponseEntity<ApiResponseDto<ApplicationFormResponseDto>> getApplication(
            @PathVariable Long clubId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        String studentId = userDetails.getUsername();
        ApplicationFormResponseDto responseDto = memberClubService.findMemberInfoWithQuestions(clubId, studentId);

        ApiResponseDto<ApplicationFormResponseDto> apiResponseDto = ApiResponseDto.success(
                responseDto,
                "[MEMBER] 동아리 지원서 조회에 성공했습니다."
        );

        return ResponseEntity.ok(apiResponseDto);
    }


    @PostMapping("/{clubId}/apply")
    public ResponseEntity<ApiResponseDto<ApplicationResponseDto>> createApplication(
            @PathVariable Long clubId,
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody ApplicationRequestDto applicationRequestDto
    ) {
        String studentId = userDetails.getUsername();
        List<AnswerRequestDto> ard = applicationRequestDto.getQuestionAndAnswer();
        String fileKey = applicationRequestDto.getFileKey();

        ApplicationResponseDto responseDto = memberClubService.saveApplication(clubId, studentId, ard, fileKey);
        ApiResponseDto<ApplicationResponseDto> apiResponseDto = ApiResponseDto.success(
                responseDto,
                "[MEMBER] 지원서 제출에 성공했습니다."
        );

        return ResponseEntity.ok(apiResponseDto);
    }

    @PostMapping("/application/upload-url")
    public ResponseEntity<ApiResponseDto<PreSignedUrlResponse>> getUploadUrl(
            @RequestBody UploadUrlRequest request
    ) {
        PreSignedUrlResponse urlResponse = fileUploadService.createPendingFileAndUrl(
                request.getOriginalFileName(),
                request.getContentType()
        );

        ApiResponseDto<PreSignedUrlResponse> response =
                ApiResponseDto.success(urlResponse, "[MEMBER] OCI 업로드 URL 생성 성공했습니다.");

        return ResponseEntity.ok(response);
    }

}
