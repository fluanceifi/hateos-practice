# SMU-CLUB 패키지 구조 명세

> 작성일: 2026-03-13
> 기준: REFACTORING_SPEC.md 전체 반영 (OCI→AWS, Smunity 제거, 세션 인증, 비로그인 지원)

---

## 변경 요약

| 구분 | 기존 | 변경 |
|------|------|------|
| 인증 패키지 | `auth/` (JWT, Smunity 연동) | `auth/` (세션 기반으로 축소) |
| 회원 패키지 | `member/` (학번 기반 Member) | **제거** → `operator/`로 대체 |
| 지원서 패키지 | `club/` 내부 ClubMember 기반 | **신규** `application/` 패키지로 분리 |
| OCI 패키지 | `util/oci/` | **제거** → `infrastructure/s3/`로 대체 |
| LLM 패키지 | 없음 | **신규** `infrastructure/llm/` 추가 |
| 알림 패키지 | `util/discord/` | `util/alert/`로 이름 변경 (기능 확장) |
| 스케줄러 | `util/scheduler/Oracle*` | `util/scheduler/S3*`로 이름 변경 |

---

## 최종 패키지 트리

```
com.example.smu_club
│
├── SmuClubApplication.java
│
├── domain/                                      # 엔티티 & Enum (순수 도메인 모델)
│   ├── Club.java
│   ├── ClubImage.java
│   ├── Application.java                         # ★ NEW: ClubMember 대체 (비로그인 지원서)
│   ├── Operator.java                            # ★ NEW: Member 대체 (운영자 세션 계정)
│   ├── EmailRetryQueue.java
│   ├── FileMetaData.java
│   ├── Question.java
│   ├── Answer.java
│   ├── BaseTimeEntity.java
│   ├── AllowedFileType.java
│   ├── ApplicationStatus.java                   # ★ RENAMED: ClubMemberStatus → ApplicationStatus
│   ├── EmailFailReason.java                     # ★ NEW: SES_AUTH_FAILED, RATE_LIMIT 등
│   ├── EmailStatus.java
│   ├── FileStatus.java
│   ├── QuestionContentType.java
│   └── RecruitingStatus.java
│
├── auth/                                        # 운영자 세션 인증
│   ├── controller/
│   │   └── AuthController.java                  # POST /auth/login, POST /auth/logout
│   ├── dto/
│   │   ├── LoginRequest.java
│   │   └── LoginResponse.java                   # { loginId, clubName, clubId }
│   ├── security/
│   │   ├── OperatorPrincipal.java               # ★ NEW: UserDetails 구현체
│   │   └── OperatorDetailsService.java          # ★ NEW: UserDetailsService 구현
│   └── handler/
│       ├── LoginSuccessHandler.java             # ★ NEW: JSON 응답 반환
│       └── LoginFailureHandler.java             # ★ NEW: 401 JSON 응답
│
│   # ✂ 제거: jwt/, external/, service/AuthService, service/TokenService
│   # ✂ 제거: dto/JwtTokenResponse, dto/ReissueRequest, dto/SignupRequest
│   # ✂ 제거: dto/UnivAuthRequest, dto/UnivUserInfoResponse
│
├── operator/                                    # ★ NEW: 운영자 도메인
│   ├── repository/
│   │   └── OperatorRepository.java              # findByLoginId()
│   └── service/
│       └── OperatorService.java                 # 운영자 정보 조회, lastLoginAt 갱신
│
│   # ✂ 제거: member/ 패키지 전체
│   #   (MemberController, MemberService, MemberRepository, member/dto/* 전체)
│
├── club/                                        # 동아리 도메인
│   ├── controller/
│   │   ├── GuestClubController.java             # GET /public/clubs/**
│   │   └── OwnerClubController.java             # /owner/club/** (동아리 등록/수정/관리)
│   │   # ✂ 제거: MemberClubController.java
│   ├── dto/
│   │   ├── ClubInfoRequest.java
│   │   ├── ClubInfoResponse.java
│   │   ├── ClubResponseDto.java
│   │   ├── ClubsResponseDto.java
│   │   ├── ClubImagesResponseDto.java
│   │   ├── ManagedClubResponse.java
│   │   ├── UploadUrlRequest.java
│   │   └── UploadUrlListRequest.java
│   ├── repository/
│   │   ├── ClubRepository.java
│   │   └── ClubImageRepository.java
│   └── service/
│       ├── GuestClubService.java
│       └── OwnerClubService.java
│       # ✂ 제거: MemberClubService.java, ClubMemberRelationService.java
│
├── application/                                 # ★ NEW: 지원서 도메인 (비로그인 지원)
│   ├── controller/
│   │   ├── PublicApplicationController.java     # POST /public/clubs/{clubId}/apply
│   │   │                                        # POST /public/clubs/application/upload-url
│   │   └── OwnerApplicationController.java      # GET /owner/club/{clubId}/applicants
│   │                                            # PATCH /owner/club/{clubId}/applicants/{id}/status
│   │                                            # GET /owner/club/{clubId}/applicants/excel
│   ├── dto/
│   │   ├── ApplicationSubmitRequest.java        # 비로그인 지원서 제출 요청
│   │   ├── ApplicationSubmitResponse.java
│   │   ├── ApplicantListResponse.java
│   │   ├── ApplicantDetailResponse.java
│   │   ├── ApplicantStatusUpdateRequest.java
│   │   ├── ApplicantExcelDto.java
│   │   └── UploadUrlResponse.java               # Pre-Signed URL 응답
│   ├── repository/
│   │   └── ApplicationRepository.java           # existsByApplicantEmailAndClubId()
│   └── service/
│       ├── ApplicationService.java              # 지원서 제출, 중복 검증
│       └── OwnerApplicationService.java         # 운영자 지원자 조회/상태 변경
│
├── question/                                    # 지원 질문 관리
│   ├── controller/
│   │   └── QuestionController.java              # /owner/club/{clubId}/questions
│   ├── dto/
│   │   ├── QuestionRequest.java
│   │   └── QuestionResponse.java
│   ├── repository/
│   │   └── QuestionRepository.java
│   └── service/
│       └── QuestionService.java
│
├── answer/                                      # 지원 답변 (Application에 종속)
│   ├── dto/
│   │   ├── AnswerRequestDto.java
│   │   └── AnswerResponseDto.java
│   └── repository/
│       └── AnswerRepository.java
│
├── email/                                       # 이메일 시스템 (AWS SES)
│   ├── repository/
│   │   └── EmailRetryQueueRepository.java
│   └── service/
│       ├── AwsSesEmailService.java              # ★ NEW: SesV2Client 기반 발송
│       └── EmailRetryService.java               # 재시도 로직 (failReason 분기 포함)
│
├── infrastructure/                              # ★ NEW: 외부 인프라 어댑터
│   ├── s3/
│   │   └── AwsS3Service.java                   # S3 업로드, Pre-Signed URL, 파일 삭제
│   └── llm/
│       └── LlmDescriptionService.java          # Claude API 연동 (동아리 소개 강화)
│
├── config/                                      # Spring 설정
│   ├── SecurityConfig.java                      # ★ CHANGED: 세션 기반, Form Login
│   ├── CustomAuthenticationEntryPoint.java      # 인증 실패 JSON 응답
│   ├── AsyncConfig.java                         # mailExecutor 스레드풀
│   ├── AwsS3Config.java                         # ★ NEW: S3Client, S3Presigner 빈
│   ├── AwsSesConfig.java                        # ★ NEW: SesV2Client 빈
│   ├── AppConfig.java                           # RestClient 등 공통 빈
│   ├── JpaConfig.java
│   └── SwaggerConfig.java
│   # ✂ 제거: JwtAuthenticationFilter.java
│   # ✂ 제거: SecurityContextDebugFilter.java
│
├── common/                                      # 공통 모듈
│   ├── ApiResponseDto.java
│   ├── file/
│   │   └── FileUploadService.java               # 파일 타입/크기 검증
│   └── fileMetaData/
│       ├── FileMetaDataRepository.java
│       └── FileMetaDataService.java
│
├── exception/                                   # 예외 처리
│   ├── GlobalExceptionHandler.java
│   └── custom/
│       ├── ClubNotFoundException.java
│       ├── ClubNotRecruitmentPeriodException.java
│       ├── ClubRecruitmentNotClosedException.java
│       ├── ClubsNotFoundException.java
│       ├── DuplicateClubNameException.java
│       ├── DuplicateApplicationException.java   # ★ NEW: 중복 지원
│       ├── EmptyClubQuestionException.java
│       ├── EmptyEmailListException.java
│       ├── ExcelException.java
│       ├── IllegalClubStateException.java
│       ├── NotAllowedFileType.java
│       ├── NotClosedRecruitment.java
│       ├── QuestionNotFoundException.java
│       ├── AwsS3Exception.java                  # ★ NEW (OciUploadException 등 대체)
│       ├── AwsSesException.java                 # ★ NEW
│       ├── OperatorNotFoundException.java       # ★ NEW
│       # ✂ 제거: OciDeletionException, OciSearchException, OciUploadException
│       # ✂ 제거: ExpiredTokenException, InvalidRefreshTokenException, InvalidTokenException
│       # ✂ 제거: MemberAlreadyExistsException, MemberNotFoundException, LoginFailedException
│       # ✂ 제거: UnivAuthenticationFailedException, AuthorizationException
│       # ✂ 제거: ClubMemberNotFoundException, InvalidRefreshTokenException
│       └── ApplicationNotFoundException.java    # ★ NEW
│
└── util/                                        # 유틸리티
    ├── ExcelService.java
    ├── RecruitmentService.java
    │
    ├── alert/                                   # ★ RENAMED: discord/ → alert/
    │   ├── annotation/
    │   │   └── DiscordAlert.java                # AlertLevel, onSuccess 필드 추가
    │   ├── aspect/
    │   │   └── DiscordAlertAspect.java          # 예외 타입별 레벨 자동 결정
    │   └── service/
    │       └── DiscordAlertService.java         # CRITICAL/ERROR/WARN 채널 분리
    │
    └── scheduler/                               # 스케줄링 (ShedLock 적용)
        ├── EmailRetryScheduler.java
        ├── S3StorageCleanupScheduler.java       # ★ RENAMED: OracleStorageCleanup → S3
        └── RecruitmentAutoClosureScheduler.java
        # ✂ 제거: expiredclubmember/ (비로그인 구조에서 불필요)
```

---

## 도메인별 책임 요약

| 패키지 | 책임 | 주요 API |
|--------|------|---------|
| `auth` | 운영자 세션 로그인/로그아웃 | `POST /auth/login` |
| `operator` | 운영자 계정 조회 | 내부 서비스 전용 |
| `club` | 동아리 CRUD, 목록/상세 조회 | `GET /public/clubs/**`, `/owner/club/**` |
| `application` | 비로그인 지원서 제출, 운영자 지원자 관리 | `POST /public/clubs/{id}/apply`, `/owner/club/{id}/applicants/**` |
| `question` | 동아리 지원 질문 관리 | `/owner/club/{id}/questions` |
| `answer` | 지원 답변 저장 | `application` 서비스에서 내부 호출 |
| `email` | AWS SES 발송, 재시도 큐 | 내부 서비스 전용 |
| `infrastructure/s3` | S3 파일 업로드, Pre-Signed URL | `application` 서비스에서 호출 |
| `infrastructure/llm` | Claude API 동아리 소개 강화 | `POST /owner/club/{id}/description/enhance` |
| `config` | Spring 빈 설정, Security 설정 | - |
| `common` | 공통 응답 포맷, 파일 검증 | - |
| `exception` | 전역 예외 처리 | - |
| `util/alert` | Discord 웹훅 알림 (AOP) | - |
| `util/scheduler` | 이메일 재시도, S3 정리, 모집 마감 | - |

---

## 제거 파일 목록 (전체)

```
# Smunity / JWT 관련 (auth 패키지)
auth/external/UnivApiClient.java
auth/external/UnivApiClientImpl.java
auth/external/SlowMockUnivApiClient.java
auth/jwt/JwtTokenProvider.java
auth/service/AuthService.java
auth/service/TokenService.java
auth/dto/JwtTokenResponse.java
auth/dto/ReissueRequest.java
auth/dto/SignupRequest.java
auth/dto/UnivAuthRequest.java
auth/dto/UnivUserInfoResponse.java
auth/controller/PublicAuthController.java

# Member 패키지 전체
member/controller/MemberController.java
member/service/MemberService.java
member/repository/MemberRepository.java
member/dto/*.java (9개 파일)

# OCI 관련
util/oci/OciStorageService.java
util/oci/OCICleanupEvent.java
util/scheduler/OracleStorageCleanupScheduler.java
exception/custom/OciDeletionException.java
exception/custom/OciSearchException.java
exception/custom/OciUploadException.java

# JWT/인증 관련 예외 및 필터
config/JwtAuthenticationFilter.java
config/SecurityContextDebugFilter.java
exception/custom/ExpiredTokenException.java
exception/custom/InvalidRefreshTokenException.java
exception/custom/InvalidTokenException.java
exception/custom/UnivAuthenticationFailedException.java

# Member 관련 예외
exception/custom/MemberAlreadyExistsException.java
exception/custom/MemberNotFoundException.java
exception/custom/LoginFailedException.java
exception/custom/ClubMemberNotFoundException.java
exception/custom/AuthorizationException.java

# ClubMember 기반 (Application으로 통합)
domain/ClubMember.java
domain/ClubMemberStatus.java
domain/ClubRole.java
domain/Role.java
club/repository/ClubMemberRepository.java
club/service/ClubMemberRelationService.java
club/service/MemberClubService.java
club/controller/MemberClubController.java
club/dto/ApplicationFormResponseDto.java  → application/dto로 이관
club/dto/ApplicationRequestDto.java       → application/dto로 이관
club/dto/ApplicationResponseDto.java      → application/dto로 이관
club/dto/ApplicantDetailViewResponse.java → application/dto로 이관
club/dto/ApplicantInfoResponse.java       → application/dto로 이관
club/dto/ApplicantResponse.java           → application/dto로 이관
club/dto/ApplicantStatusUpdateRequest.java → application/dto로 이관
club/dto/ApplicantExcelDto.java           → application/dto로 이관

# 기타
util/expiredclubmember/ (패키지 전체 3개 파일)
util/discord/TempAopTestController.java
```

---

## 신규 파일 목록

```
# 운영자 인증
auth/security/OperatorPrincipal.java
auth/security/OperatorDetailsService.java
auth/handler/LoginSuccessHandler.java
auth/handler/LoginFailureHandler.java
auth/dto/LoginRequest.java              (기존 유지/재정의)
auth/dto/LoginResponse.java             (신규)

# 운영자 도메인
domain/Operator.java
operator/repository/OperatorRepository.java
operator/service/OperatorService.java

# 지원서 도메인
domain/Application.java
domain/ApplicationStatus.java          (ClubMemberStatus 이름 변경)
domain/EmailFailReason.java
application/controller/PublicApplicationController.java
application/controller/OwnerApplicationController.java
application/dto/ApplicationSubmitRequest.java
application/dto/ApplicationSubmitResponse.java
application/dto/ApplicantListResponse.java
application/dto/ApplicantDetailResponse.java
application/dto/ApplicantStatusUpdateRequest.java
application/dto/ApplicantExcelDto.java
application/dto/UploadUrlResponse.java
application/repository/ApplicationRepository.java
application/service/ApplicationService.java
application/service/OwnerApplicationService.java

# AWS 인프라
config/AwsS3Config.java
config/AwsSesConfig.java
infrastructure/s3/AwsS3Service.java
infrastructure/llm/LlmDescriptionService.java
email/service/AwsSesEmailService.java

# 예외
exception/custom/AwsS3Exception.java
exception/custom/AwsSesException.java
exception/custom/OperatorNotFoundException.java
exception/custom/DuplicateApplicationException.java
exception/custom/ApplicationNotFoundException.java
```

---

## 마이그레이션 DB 변경 사항

| 테이블 | 변경 내용 |
|--------|---------|
| `members` | **제거** |
| `operators` | **신규** (id, login_id, password, club_name, club_id, created_at, last_login_at) |
| `club_members` | **신규명** `applications`으로 변경, member_id FK 제거, applicant_name/email/phone 컬럼 추가 |
| `shedlock` | **신규** (ShedLock 분산 락용) |
| `email_retry_queue` | fail_reason 컬럼 추가 |
