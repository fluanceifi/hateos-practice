package com.example.smu_club.exception;


import com.example.smu_club.common.ApiResponseDto;
import com.example.smu_club.exception.custom.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;


@RestControllerAdvice
public class GlobalExceptionHandler {

    /*
    Auth 예외 관련
     */
    // [로그인 실패] 예외 처리 -> 401 Unauthorized 응답
    @ExceptionHandler(LoginFailedException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleLoginFailedException(LoginFailedException e) {
        ApiResponseDto<Object> response = ApiResponseDto.fail("LOGIN_FAILED", e.getMessage());
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    // [미가입 회원] 예외 처리 -> 404 Not Found 응답
    @ExceptionHandler(MemberNotFoundException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleMemberNotFoundException(MemberNotFoundException e) {
        ApiResponseDto<Object> response = ApiResponseDto.fail("MEMBER_NOT_FOUND", e.getMessage());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    // [유효하지 않은 토큰 예외처리] RefreshToken 관련 -> 401 Unauthorized 응답
    // 예시 상황 : JWT 토큰이 없거나, 서명이 잘못되었거나 , 권한정보가 없거나
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleInvalidTokenException(InvalidRefreshTokenException e) {
        ApiResponseDto<Object> response = ApiResponseDto.fail("INVALID_REFRESH_TOKEN", e.getMessage());
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    // [이미 가입된 회원이 다시 인증시도] -> 409 Conflict 응답
    @ExceptionHandler(MemberAlreadyExistsException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleMemberAlreadyExistsException(MemberAlreadyExistsException e) {

        ApiResponseDto<Object> response = ApiResponseDto.fail("MEMBER_ALREADY_EXISTS", e.getMessage());
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    // [학교 API 인증에 실패했을 때] -> 401 Unauthorized 응답
    @ExceptionHandler(UnivAuthenticationFailedException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleUnivAuthenticationFailedException(UnivAuthenticationFailedException e) {
        ApiResponseDto<Object> response = ApiResponseDto.fail("UNIV_AUTH_FAILED", e.getMessage());
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    /**
     * 외부 API 호출 실패 시 처리
     */
    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleRestClientResponseException(RestClientResponseException e) {
        ApiResponseDto<Object> response = ApiResponseDto.fail("EXTERNAL_API_ERROR", e.getMessage());
        return new ResponseEntity<>(response, HttpStatus.valueOf(e.getRawStatusCode()));
    }


    /*
    Club 예외 관련
     */

    // [클럽 정보를 찾을 수 없음] -> 404 Not Found 반환
    @ExceptionHandler(ClubNotFoundException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleClubNotFoundException(ClubNotFoundException e) {
        ApiResponseDto<Object> response = ApiResponseDto.fail("CLUB_NOT_FOUND", e.getMessage());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    // [클럽 정보들을 찾을 수 없음] -> 404 Not Found 반환
    @ExceptionHandler(ClubsNotFoundException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleClubsNotFoundException(ClubsNotFoundException e) {
        ApiResponseDto<Object> response = ApiResponseDto.fail("CLUBS_NOT_FOUND", e.getMessage());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    // [UPCOMING 이 아닌 동아리에서 모집예정 버튼을 누르면] -> 409 CONFLICT 반환 (시나리오 상 일어날 수 없음)
    @ExceptionHandler(IllegalClubStateException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleIllegalClubStateException(IllegalClubStateException e) {
        ApiResponseDto<Object> response = ApiResponseDto.fail("ILLEGAL_CLUB", e.getMessage());
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    // [OWNER] 동아리 등록하려하는데 이름이 기존하고 겹칠 때
    @ExceptionHandler(DuplicateClubNameException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleDuplicateClubName(DuplicateClubNameException e) {
        ApiResponseDto<Object> response =
                ApiResponseDto.fail("DUPLICATE_CLUB_NAME", e.getMessage());
        return new ResponseEntity<>(response, HttpStatus.CONFLICT); // 409
    }

    // [OWNER] 동아리 CLOSED 전에 동아리 상세 정보 조회하려고 하면
    @ExceptionHandler(ClubRecruitmentNotClosedException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleClubRecruitmentNotClosedException(ClubRecruitmentNotClosedException e) {
        ApiResponseDto<Object> response = ApiResponseDto.fail("CLUB_RECRUITMENT_NOT_CLOSED", e.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /*
    ClubMember 예외 관련
     */

    // [ClubMember 테이블에서 정보를 찾을 수 없다면] -> 404 NOT FOUND 반환
    @ExceptionHandler(ClubMemberNotFoundException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleClubMemberNotFoundException(ClubMemberNotFoundException e) {
        ApiResponseDto<Object> response = ApiResponseDto.fail("CLUB_MEMBER_NOT_FOUND", e.getMessage());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    // [ClubMember 에는 있지만 권한이 OWNER 이 아니라면] ->  403 Forbidden 반환
    @ExceptionHandler(AuthorizationException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleAuthorizationException(AuthorizationException e) {
        ApiResponseDto<Object> response = ApiResponseDto.fail("AUTHORIZATION", e.getMessage());
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    /*
    Question 예외 관련
     */
    @ExceptionHandler(QuestionNotFoundException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleQuestionNotFoundException(QuestionNotFoundException e) {
        ApiResponseDto<Object> response = ApiResponseDto.fail("QUESTION_NOT_FOUND", e.getMessage());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    /*
    MemberClub 예외 관련
     */
    @ExceptionHandler(ClubNotRecruitmentPeriodException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleClubNotRecruitmentPeriod(ClubNotRecruitmentPeriodException e) {
        ApiResponseDto<Object> response = ApiResponseDto.fail("CLUB_NOT_RECRUITMENT_PERIOD", e.getMessage());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }



    /**
     * OCI 파일 업로드 실패 시 처리
     */
    @ExceptionHandler(OciUploadException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleOciUploadException(OciUploadException e) {
        ApiResponseDto<Object> response = ApiResponseDto.fail("OCI_UPLOAD", e.getMessage());
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(OciDeletionException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleOciDeletionException(OciDeletionException e) {
        ApiResponseDto<Object> response = ApiResponseDto.fail("OCI_DELETION", e.getMessage());
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }


    //이메일 전송관련
    @ExceptionHandler(EmptyEmailListException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleEmptyEmailListException(EmptyEmailListException e) {
        ApiResponseDto<Object> response = ApiResponseDto.fail("EMPTY_EMAIL_LIST", e.getMessage());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    // 엑셀 파일 다운로드 실패 관련
    @ExceptionHandler(ExcelException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleExcelException(ExcelException e) {
        ApiResponseDto<Object> response = ApiResponseDto.fail("EXCEL_ERROR", e.getMessage());
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }



    // 동아리 모집이 종료 및 확정되지 않았을 때 발생하는 예외
    @ExceptionHandler(NotClosedRecruitment.class)
    public ResponseEntity<ApiResponseDto<Object>> handleNotClosedRecruitment(NotClosedRecruitment e) {
        ApiResponseDto<Object> response = ApiResponseDto.fail("NOT_CLOSED_RECRUITMENT", e.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
}
