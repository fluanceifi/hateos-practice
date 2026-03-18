# SMU-CLUB 프로덕션 레벨 패키지 설계

> 작성일: 2026-03-13
> 전제: 현재 코드와 무관하게 처음부터 설계한다면

---

## 설계 철학

현재 코드는 **레이어 기반** 구조다.
```
domain/ ← 모든 엔티티가 한 곳
config/ ← 모든 설정이 한 곳
service/ ← 모든 비즈니스 로직
```

이 방식의 문제는 프로젝트가 커질수록 **레이어 내부가 비대해진다**.
`domain/`에 17개 엔티티가 섞이고, `service/`에서 어디서 어디를 의존하는지 파악하기 어려워진다.

프로덕션에서는 **기능(Feature) 중심**으로 패키지를 먼저 나누고,
그 안에서 레이어를 분리한다. 이를 **Vertical Slice Architecture** 라고 한다.

```
기능 모듈
  └── domain/    (엔티티, 레포지토리 인터페이스)
  └── service/   (유스케이스, Command/Query 분리)
  └── infra/     (JPA 구현체, 외부 어댑터)
  └── web/       (컨트롤러, DTO)
```

추가로 세 가지 패턴을 핵심으로 삼는다:

1. **CQRS (Command/Query 분리)** — 읽기 서비스와 쓰기 서비스 완전 분리
2. **Port & Adapter** — 외부 시스템(S3, SES, Claude API)은 인터페이스 뒤에 숨김
3. **Domain Event** — 상태 변경 후 부수 효과(이메일 발송 등)는 이벤트로 처리

---

## 현재 방식 vs 프로덕션 방식 비교

| 항목 | 현재 | 프로덕션 |
|------|------|---------|
| 패키지 구조 | 레이어 기반 (domain/, service/, ...) | 기능 기반 (club/, apply/, ...) |
| 서비스 분리 | 하나의 서비스에 읽기/쓰기 혼재 | Command/Query 서비스 분리 |
| 외부 시스템 | 서비스에서 직접 호출 | 포트 인터페이스 → 어댑터 |
| 이메일 발송 | 서비스에서 직접 EmailService 호출 | 도메인 이벤트 발행 → 핸들러 |
| 예외 관리 | 25개 독립 예외 클래스 | ErrorCode Enum + BusinessException 1개 |
| 엔티티 노출 | 엔티티가 컨트롤러 응답에 직접 사용됨 | 엔티티는 서비스 레이어 밖으로 절대 노출 안 함 |
| 레포지토리 | JPA 레포지토리를 서비스가 직접 의존 | 포트 인터페이스를 서비스가 의존, JPA는 구현체 |

---

## 최종 패키지 트리

```
com.smuclub                              ← 그룹 ID도 com.example 제거
│
├── SmuClubApplication.java
│
│
├── shared/                              # 공유 커널 (모든 모듈에서 사용)
│   ├── domain/
│   │   └── BaseEntity.java             # createdAt, updatedAt (공통 Auditing)
│   ├── exception/
│   │   ├── BusinessException.java      # ★ 모든 비즈니스 예외의 단일 부모
│   │   ├── ErrorCode.java              # ★ Enum으로 전체 에러 코드/메시지 관리
│   │   └── GlobalExceptionHandler.java
│   ├── response/
│   │   └── ApiResponse.java            # { success, data, error } 통일 포맷
│   └── support/
│       └── SliceUtils.java             # 페이지네이션 유틸
│
│
├── auth/                                # 인증 모듈
│   ├── domain/
│   │   ├── Operator.java               # 운영자 엔티티
│   │   └── OperatorRepository.java     # 포트 인터페이스 (domain이 정의)
│   ├── service/
│   │   └── OperatorDetailsService.java # UserDetailsService 구현
│   ├── infra/
│   │   └── OperatorRepositoryImpl.java # JPA 구현체
│   └── web/
│       ├── AuthController.java
│       └── dto/
│           ├── LoginRequest.java
│           └── LoginResponse.java
│
│
├── club/                                # 동아리 모듈
│   ├── domain/
│   │   ├── Club.java
│   │   ├── ClubImage.java
│   │   ├── RecruitingStatus.java
│   │   ├── ClubRepository.java         # 포트 인터페이스
│   │   └── ClubImageRepository.java
│   ├── service/
│   │   ├── ClubQueryService.java       # ★ 읽기 전용 (SELECT만)
│   │   └── ClubCommandService.java     # ★ 쓰기 전용 (등록/수정/삭제/상태변경)
│   ├── infra/
│   │   ├── ClubRepositoryImpl.java
│   │   └── ClubImageRepositoryImpl.java
│   └── web/
│       ├── PublicClubController.java   # GET /public/clubs/**
│       ├── OwnerClubController.java    # /owner/club/**
│       └── dto/
│           ├── request/
│           │   ├── ClubCreateRequest.java
│           │   └── ClubUpdateRequest.java
│           └── response/
│               ├── ClubSummaryResponse.java
│               └── ClubDetailResponse.java
│
│
├── apply/                               # 지원서 모듈 (비로그인)
│   ├── domain/
│   │   ├── Application.java
│   │   ├── ApplicationStatus.java
│   │   ├── ApplicationRepository.java  # 포트 인터페이스
│   │   └── event/
│   │       └── ApplicationStatusChangedEvent.java  # ★ 도메인 이벤트
│   ├── service/
│   │   ├── ApplicationSubmitService.java   # 지원서 제출 (Command)
│   │   └── ApplicationQueryService.java    # 지원자 조회 (Query)
│   │   └── ApplicationManageService.java   # 상태 변경, 메모 (Command)
│   ├── infra/
│   │   └── ApplicationRepositoryImpl.java
│   └── web/
│       ├── PublicApplicationController.java  # POST /public/clubs/{id}/apply
│       ├── OwnerApplicationController.java   # /owner/club/{id}/applicants/**
│       └── dto/
│           ├── request/
│           │   ├── ApplicationSubmitRequest.java
│           │   └── ApplicantStatusUpdateRequest.java
│           └── response/
│               ├── ApplicantSummaryResponse.java
│               └── ApplicantDetailResponse.java
│
│
├── question/                            # 질문/답변 모듈
│   ├── domain/
│   │   ├── Question.java
│   │   ├── Answer.java
│   │   ├── QuestionRepository.java
│   │   └── AnswerRepository.java
│   ├── service/
│   │   ├── QuestionQueryService.java
│   │   └── QuestionCommandService.java
│   ├── infra/
│   │   ├── QuestionRepositoryImpl.java
│   │   └── AnswerRepositoryImpl.java
│   └── web/
│       ├── QuestionController.java
│       └── dto/
│
│
├── notification/                        # 알림 모듈 (이메일 + Discord)
│   │
│   ├── email/
│   │   ├── domain/
│   │   │   ├── EmailRetryQueue.java
│   │   │   ├── EmailFailReason.java
│   │   │   └── EmailRetryQueueRepository.java
│   │   ├── port/
│   │   │   └── EmailSendPort.java      # ★ 포트 인터페이스 (SES 또는 Mock 교체 가능)
│   │   ├── service/
│   │   │   ├── EmailNotificationService.java
│   │   │   └── EmailRetryService.java
│   │   ├── handler/
│   │   │   └── ApplicationEventHandler.java  # ★ 도메인 이벤트 수신 → 이메일 발송
│   │   │                                     #   @TransactionalEventListener
│   │   ├── infra/
│   │   │   ├── SesEmailAdapter.java     # EmailSendPort 구현체 (실제 SES)
│   │   │   └── EmailRetryQueueRepositoryImpl.java
│   │   └── scheduler/
│   │       └── EmailRetryScheduler.java
│   │
│   └── discord/
│       ├── annotation/
│       │   └── DiscordAlert.java        # AlertLevel(CRITICAL/ERROR/WARN), onSuccess
│       ├── aspect/
│       │   └── DiscordAlertAspect.java  # 예외 타입별 레벨 자동 판정
│       └── service/
│           └── DiscordAlertService.java # 채널 분리 (CRITICAL/ERROR/WARN)
│
│
├── storage/                             # 파일 저장소 모듈
│   ├── port/
│   │   └── FileStoragePort.java        # ★ 포트 인터페이스
│   │                                   #   upload(), delete(), getPublicUrl()
│   ├── domain/
│   │   ├── FileMetaData.java
│   │   ├── FileStatus.java
│   │   ├── AllowedFileType.java
│   │   └── FileMetaDataRepository.java
│   ├── service/
│   │   ├── FileUploadService.java      # 파일 타입/크기 검증 + 메타데이터 관리
│   │   └── FileMetaDataService.java    # 고아 파일 정리, 상태 관리
│   ├── infra/
│   │   ├── S3FileStorageAdapter.java   # FileStoragePort 구현체
│   │   └── FileMetaDataRepositoryImpl.java
│   └── scheduler/
│       └── S3OrphanFileCleanupScheduler.java
│
│
├── llm/                                 # LLM 모듈
│   ├── port/
│   │   └── LlmPort.java               # ★ 포트 인터페이스 → enhance(String) : String
│   ├── service/
│   │   └── DescriptionEnhanceService.java  # Rate Limiting + 프롬프트 관리
│   ├── infra/
│   │   └── ClaudeApiAdapter.java      # LlmPort 구현체 (claude-haiku)
│   └── web/
│       ├── LlmController.java         # POST /owner/club/{id}/description/enhance
│       └── dto/
│           ├── EnhanceRequest.java
│           └── EnhanceResponse.java
│
│
├── schedule/                            # 전역 스케줄러 (ShedLock 설정)
│   └── ShedLockConfig.java             # @EnableSchedulerLock 설정만
│                                       # 각 스케줄러는 해당 모듈에 위치
│
│
└── config/                              # Spring 설정
    ├── SecurityConfig.java              # 세션 기반, Form Login
    ├── AsyncConfig.java                 # mailExecutor 스레드풀
    ├── AwsConfig.java                   # S3Client, S3Presigner, SesV2Client 빈
    ├── JpaConfig.java                   # Auditing 설정
    └── SwaggerConfig.java
```

---

## 핵심 패턴 상세 설명

### 1. ErrorCode Enum — 예외 25개 → 1개

**현재 방식 (문제):**
```java
// 예외 클래스가 25개 이상 → 파일만 많고 일관성 없음
throw new ClubNotFoundException();
throw new MemberNotFoundException();
throw new OciUploadException();
```

**프로덕션 방식:**
```java
// shared/exception/ErrorCode.java
public enum ErrorCode {

    // Club
    CLUB_NOT_FOUND("C001", "동아리를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    CLUB_NAME_DUPLICATE("C002", "이미 존재하는 동아리 이름입니다", HttpStatus.CONFLICT),
    CLUB_NOT_RECRUITING("C003", "모집 중인 동아리가 아닙니다", HttpStatus.BAD_REQUEST),

    // Apply
    APPLICATION_NOT_FOUND("A001", "지원서를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    APPLICATION_DUPLICATE("A002", "이미 지원한 동아리입니다", HttpStatus.CONFLICT),

    // Storage
    FILE_TYPE_NOT_ALLOWED("F001", "허용되지 않는 파일 형식입니다", HttpStatus.BAD_REQUEST),
    FILE_SIZE_EXCEEDED("F002", "파일 크기 제한을 초과했습니다", HttpStatus.BAD_REQUEST),
    FILE_UPLOAD_FAILED("F003", "파일 업로드에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),

    // Notification
    EMAIL_SEND_FAILED("N001", "이메일 발송에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    EMAIL_AUTH_FAILED("N002", "이메일 서비스 인증에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),

    // Auth
    OPERATOR_NOT_FOUND("O001", "존재하지 않는 계정입니다", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED("O002", "인증이 필요합니다", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("O003", "접근 권한이 없습니다", HttpStatus.FORBIDDEN);

    private final String code;
    private final String message;
    private final HttpStatus status;
}

// shared/exception/BusinessException.java
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}

// 사용
throw new BusinessException(ErrorCode.CLUB_NOT_FOUND);
throw new BusinessException(ErrorCode.APPLICATION_DUPLICATE);
```

---

### 2. Port & Adapter — 외부 시스템 격리

**현재 방식 (문제):**
```java
// 서비스가 SES SDK에 직접 의존 → 테스트 시 실제 AWS 호출
public class EmailRetryService {
    private final SesV2Client sesV2Client; // SDK 직접 의존
}
```

**프로덕션 방식:**
```java
// notification/email/port/EmailSendPort.java
public interface EmailSendPort {
    void send(String to, String subject, String htmlBody);
}

// notification/email/infra/SesEmailAdapter.java
@Component
public class SesEmailAdapter implements EmailSendPort {
    private final SesV2Client sesV2Client;

    @Override
    public void send(String to, String subject, String htmlBody) {
        // SES SDK 호출
    }
}

// 테스트 시
@TestConfiguration
public class MockEmailConfig {
    @Bean
    public EmailSendPort emailSendPort() {
        return (to, subject, body) -> {}; // 아무것도 안 함
    }
}

// 마찬가지로
public interface FileStoragePort {
    String uploadAndGetKey(MultipartFile file);
    String generatePresignedUploadUrl(String fileKey, String contentType);
    void delete(String fileKey);
    String getPublicUrl(String fileKey);
}

public interface LlmPort {
    String enhance(String systemPrompt, String userInput);
}
```

서비스는 인터페이스만 알고, 구현체는 Spring이 주입.
AWS를 GCP로 바꿔도 서비스 코드는 0줄 변경.

---

### 3. CQRS — 읽기/쓰기 분리

**현재 방식 (문제):**
```java
// OwnerClubService 하나에 모든 것이 섞임
public class OwnerClubService {
    List<Club> getManagedClubs(Long operatorId) { ... }    // 읽기
    void registerClub(ClubCreateRequest req) { ... }       // 쓰기
    void updateClub(Long id, ClubUpdateRequest req) { ... } // 쓰기
    List<Applicant> getApplicants(Long clubId) { ... }     // 읽기
    void updateStatus(Long id, Status status) { ... }      // 쓰기
    // ...10개 이상 메서드
}
```

**프로덕션 방식:**
```java
// club/service/ClubQueryService.java
@Service
@Transactional(readOnly = true)  // 읽기 전용 트랜잭션 → 성능 최적화
public class ClubQueryService {
    ClubDetailResponse getClub(Long clubId) { ... }
    Page<ClubSummaryResponse> getClubs(Pageable pageable) { ... }
    List<ManagedClubResponse> getManagedClubs(Long operatorId) { ... }
}

// club/service/ClubCommandService.java
@Service
@Transactional  // 쓰기 트랜잭션
public class ClubCommandService {
    Long createClub(ClubCreateRequest request, Long operatorId) { ... }
    void updateClub(Long clubId, ClubUpdateRequest request) { ... }
    void closeRecruitment(Long clubId) { ... }
}
```

`@Transactional(readOnly = true)` 가 쿼리에만 붙으면:
- JPA flush 방지 → 성능 향상
- 실수로 쓰기가 섞이는 것 컴파일 타임에 방지
- Master/Replica DB 구성 시 자동 라우팅 가능

---

### 4. Domain Event — 부수 효과 분리

**현재 방식 (문제):**
```java
// 지원 상태 변경 서비스가 이메일 서비스에 직접 의존
// → 두 관심사가 강결합
public class ApplicationManageService {
    private final EmailNotificationService emailService; // 직접 의존

    public void updateStatus(Long id, ApplicationStatus status) {
        application.changeStatus(status);
        emailService.sendStatusEmail(application); // 직접 호출
    }
}
```

**프로덕션 방식:**
```java
// apply/domain/event/ApplicationStatusChangedEvent.java
public record ApplicationStatusChangedEvent(
    Long applicationId,
    String applicantEmail,
    ApplicationStatus newStatus
) {}

// apply/service/ApplicationManageService.java
public class ApplicationManageService {
    private final ApplicationEventPublisher eventPublisher; // Spring 이벤트만 의존

    public void updateStatus(Long id, ApplicationStatus status) {
        application.changeStatus(status);
        // 이메일이 어떻게 발송되는지 전혀 모름
        eventPublisher.publishEvent(
            new ApplicationStatusChangedEvent(id, application.getApplicantEmail(), status)
        );
    }
}

// notification/email/handler/ApplicationEventHandler.java
@Component
public class ApplicationEventHandler {

    private final EmailNotificationService emailService;

    @TransactionalEventListener(phase = AFTER_COMMIT) // 트랜잭션 커밋 후 실행
    public void handle(ApplicationStatusChangedEvent event) {
        emailService.sendStatusChangeEmail(
            event.applicantEmail(),
            event.newStatus()
        );
    }
}
```

이점:
- `ApplicationManageService`는 이메일에 대해 전혀 모름 → 단일 책임
- 이메일 핸들러를 추가해도 서비스 코드 변경 없음
- `@TransactionalEventListener(AFTER_COMMIT)` → DB 저장 성공한 경우에만 이메일 발송

---

### 5. DTO 계층 분리 — 엔티티 노출 금지

**현재 방식 (문제):**
```java
// 엔티티가 컨트롤러 응답에 직접 사용되거나
// 서비스가 엔티티를 반환
public Club getClub(Long id) {
    return clubRepository.findById(id); // 엔티티 직접 반환
}
```

**프로덕션 방식:**
```java
// 서비스는 항상 응답 DTO를 반환
// → 엔티티는 infra/service 레이어 밖으로 절대 노출 안 함

public ClubDetailResponse getClub(Long id) {
    Club club = clubRepository.findById(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.CLUB_NOT_FOUND));

    return ClubDetailResponse.from(club); // 변환은 DTO가 담당
}

// club/web/dto/response/ClubDetailResponse.java
public record ClubDetailResponse(
    Long id,
    String name,
    String description,
    String thumbnailUrl,
    RecruitingStatus recruitingStatus
) {
    public static ClubDetailResponse from(Club club) {
        return new ClubDetailResponse(
            club.getId(),
            club.getName(),
            club.getDescription(),
            club.getThumbnailUrl(),
            club.getRecruitingStatus()
        );
    }
}
```

Java 16+ `record` 타입 사용 → 불변 DTO, 보일러플레이트 최소화.

---

## 모듈 간 의존 규칙

```
★ 절대 금지 규칙

apply/ → club/        : OK (지원서는 동아리를 참조)
club/  → apply/       : ✂ 금지 (동아리가 지원서를 직접 참조하면 순환 의존)
*      → shared/      : OK (공유 커널은 모두 참조 가능)
*      → notification/ : ✂ 금지 (도메인이 알림에 직접 의존 금지 → 이벤트로만)
*      → storage/      : OK (파일 저장은 공통 인프라)
infra/ → domain/      : OK (구현체가 인터페이스 구현)
domain/ → infra/      : ✂ 금지 (도메인이 JPA에 의존하면 안 됨)
web/   → service/     : OK
service/ → web/       : ✂ 금지 (서비스가 DTO에 의존 금지 → 레이어 역전)
```

ArchUnit으로 의존 규칙을 테스트 코드로 강제할 수 있다:
```java
@AnalyzeClasses(packages = "com.smuclub")
public class ArchitectureTest {

    @ArchTest
    static final ArchRule domainShouldNotDependOnInfra =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAPackage("..infra..");

    @ArchTest
    static final ArchRule servicesShouldNotDependOnWeb =
        noClasses().that().resideInAPackage("..service..")
            .should().dependOnClassesThat()
            .resideInAPackage("..web..");
}
```

---

## 테스트 전략

```
단위 테스트 (service/)
  → 포트를 Mock으로 교체하여 외부 의존 없이 비즈니스 로직 검증
  → EmailSendPort Mock → 실제 SES 호출 없음
  → FileStoragePort Mock → 실제 S3 호출 없음

슬라이스 테스트 (web/)
  → @WebMvcTest로 컨트롤러만 테스트
  → Service는 @MockBean

통합 테스트 (infra/)
  → @DataJpaTest로 JPA 레포지토리만 테스트
  → H2 또는 TestContainers MySQL

E2E 테스트
  → @SpringBootTest + TestContainers (MySQL, LocalStack for S3)
  → 실제 HTTP 요청으로 전체 플로우 검증
```

---

## 현재 설계 대비 포기한 것들

이 구조가 완벽한 것은 아니다. 의도적으로 포기한 것들:

| 포기한 것 | 이유 |
|----------|------|
| 완전한 Hexagonal Architecture (infra 레이어에 Repository 인터페이스 분리) | 이 프로젝트 규모에서 오버엔지니어링. 모듈 내 `domain/`에 인터페이스 두는 것으로 충분 |
| DDD Aggregate + Value Object 완전 구현 | 도메인 복잡도가 낮음. Club, Application 정도에서 Aggregate 경계 나누는 것은 득보다 실이 큼 |
| Event Sourcing | 이벤트 히스토리 재생이 필요한 도메인이 아님 |
| Saga Pattern (분산 트랜잭션) | 모놀리스 구조에서 불필요 |
| gRPC / GraphQL | REST로 충분한 규모 |

**결론**: 복잡도는 필요에 의해서만 추가한다.
이 프로젝트의 규모에서 위 5가지 패턴(CQRS, Port/Adapter, Domain Event, ErrorCode Enum, DTO 분리)이 ROI가 가장 높다.
