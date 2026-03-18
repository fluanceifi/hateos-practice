# SMU-CLUB 대규모 리팩토링 명세서

> 작성일: 2026-03-13
> 목적: 프로젝트 품질 향상 및 운영 안정성 확보

---

## 현재 스택 요약

| 항목 | 현재 상태 |
|------|----------|
| 백엔드 | Java 21, Spring Boot 3.5.5, JPA, MySQL 8.0 |
| 파일 저장소 | OCI Object Storage |
| 이메일 | OCI SMTP + 지수 백오프 재시도 큐 |
| 로깅 | Log4j2 + Discord Webhook (AOP) |
| 배포 | Docker Compose, OCI Cloud, GitHub Actions |
| REST API | 공개 4개, 회원 11개, 운영진 13개 = 총 28개 |

---

## 리팩토링 주제 목록

1. [3-Tier Architecture 고가용성](#1-3-tier-architecture-고가용성)
2. [동기/비동기 - 이메일 시스템 개선](#2-동기비동기---이메일-시스템-개선)
3. [LLM 연동 - 동아리 소개 콘텐츠 생성](#3-llm-연동---동아리-소개-콘텐츠-생성)
4. [커스텀 어노테이션 기반 웹훅 알림 및 로그 레벨 정비](#4-커스텀-어노테이션-기반-웹훅-알림-및-로그-레벨-정비)
5. [thumbnailURL 개선](#5-thumbnailurl-개선)

---

## 1. 3-Tier Architecture 고가용성

### 1.1 현재 문제점

- 단일 `docker-compose.yml`로 모든 서비스(nginx, backend, db)가 한 인스턴스에서 동작
- 백엔드 인스턴스가 1개여서 배포 시 다운타임 발생
- MySQL도 단일 인스턴스 → 장애 시 서비스 전체 중단
- 스케줄러(이메일 재시도, 모집 마감, 파일 정리)가 백엔드 애플리케이션 내부에 종속

### 1.2 목표 아키텍처

```
[ Internet ]
     ↓
[ Nginx (Reverse Proxy + SSL Termination) ]
     ↓
[ Backend Cluster ]
  ├── backend-1 (Spring Boot)
  └── backend-2 (Spring Boot)
     ↓
[ MySQL (Primary) ]
  └── (선택적) Read Replica
```

### 1.3 개선 방향

#### (1) Nginx 로드밸런싱 설정

현재 `nginx.conf`에서 단일 `backend` 컨테이너를 바라보고 있음.
`upstream` 블록으로 두 개 이상의 백엔드 컨테이너로 로드밸런싱 구성.

```nginx
# nginx/nginx.conf 수정 방향
upstream backend_cluster {
    server backend-1:8080;
    server backend-2:8080;
    keepalive 32;
}

server {
    location /api/ {
        proxy_pass http://backend_cluster;
    }
}
```

#### (2) docker-compose 멀티 인스턴스

```yaml
# docker-compose.yml 수정 방향
services:
  backend-1:
    image: bongguscha/smu-club-backend:latest
    environment:
      SPRING_PROFILES_ACTIVE: prod

  backend-2:
    image: bongguscha/smu-club-backend:latest
    environment:
      SPRING_PROFILES_ACTIVE: prod
```

#### (3) 스케줄러 중복 실행 방지 (ShedLock 도입)

다중 인스턴스 환경에서는 스케줄러가 두 인스턴스에서 동시에 실행됨.
**ShedLock** 라이브러리로 DB 기반 분산 락 적용.

```gradle
// build.gradle 추가
implementation 'net.javacrumbs.shedlock:shedlock-spring:5.10.0'
implementation 'net.javacrumbs.shedlock:shedlock-provider-jdbc-template:5.10.0'
```

```java
// 스케줄러 적용 예시
@Scheduled(cron = "0 * * * * *")
@SchedulerLock(name = "emailRetryScheduler", lockAtMostFor = "50s", lockAtLeastFor = "10s")
public void retryFailedEmails() { ... }

@Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
@SchedulerLock(name = "recruitmentAutoClosureScheduler", lockAtMostFor = "23h")
public void closeExpiredRecruitments() { ... }

@Scheduled(cron = "0 0 3 * * *")
@SchedulerLock(name = "oracleStorageCleanupScheduler", lockAtMostFor = "2h")
public void cleanUpOrphanFiles() { ... }
```

```sql
-- ShedLock 테이블 DDL (MySQL)
CREATE TABLE shedlock (
    name      VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
```

#### (4) Graceful Shutdown 보장

```yaml
# application.properties
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=30s
```

롤링 배포 시 진행 중인 요청이 끊기지 않도록 보장.

#### (5) Health Check 엔드포인트

```java
// Spring Actuator 활성화
management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=when-authorized
```

nginx에서 헬스체크로 비정상 인스턴스 자동 제외.

---

## 2. 동기/비동기 - 이메일 시스템 개선

### 2.1 현재 구조

```
POST /email (동기)
  └─ 권한 검증 + 상태 PROCESSING 변경
       └─ @Async("mailExecutor") → SMTP 발송
            ├── 성공: COMPLETE
            └── 실패: EmailRetryQueue 저장

@Scheduled(0 * * * * *) EmailRetryScheduler
  └─ nextRetryDate 지난 항목 100개씩 재시도 (최대 5회)
```

### 2.2 현재 문제점

- `@Async` 스레드풀 설정이 명시적이지 않아 스레드 고갈 가능성 존재
- EmailRetryQueue 자체가 DB 테이블 → 스케줄러 부하 시 DB I/O 증가
- 재시도 로직이 스케줄러 + 서비스 레이어에 분산되어 있어 가독성 저하
- 이메일 발송 실패 원인 구분 없이 모두 동일하게 재시도 처리
- GIVE_UP 후 관리자 알림 채널 없음

### 2.3 개선 방향

#### (1) 스레드풀 명시적 설정

```java
// AsyncConfig.java
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("mailExecutor")
    public Executor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mail-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

#### (2) 재시도 실패 원인 분류

```java
public enum EmailFailReason {
    SMTP_CONNECTION_FAILED,   // 서버 연결 실패 → 재시도 유효
    INVALID_EMAIL_ADDRESS,    // 이메일 주소 오류 → 재시도 불필요
    SMTP_AUTH_FAILED,         // 인증 실패 → 재시도 불필요, 즉시 관리자 알림
    RATE_LIMIT_EXCEEDED,      // 발송 한도 초과 → 긴 백오프 적용
    UNKNOWN                   // 기타 → 기본 재시도
}
```

`EmailRetryQueue`에 `failReason` 컬럼 추가, 원인에 따라 재시도 여부 결정.

#### (3) GIVE_UP 시 Discord 알림 강화

```java
// EmailRetryService.java
private void markAsGiveUp(EmailRetryQueue task) {
    task.giveUp();
    emailRetryQueueRepository.save(task);

    // 기존 로그만 남기던 것에서 Discord 즉시 알림으로 격상
    discordAlertService.sendGiveUpAlert(task);
}
```

#### (4) 재시도 백오프 전략 개선

현재: `2^retryCount 분` (1회→2분, 2회→4분, 3회→8분, 4회→16분, 5회→GIVE_UP)
개선: `failReason`별 다른 백오프 전략 적용

```java
public Duration calculateNextRetryDelay(EmailFailReason reason, int retryCount) {
    return switch (reason) {
        case SMTP_CONNECTION_FAILED -> Duration.ofMinutes((long) Math.pow(2, retryCount));
        case RATE_LIMIT_EXCEEDED    -> Duration.ofHours(1); // 고정 1시간
        case INVALID_EMAIL_ADDRESS  -> null; // 재시도 안 함
        default                     -> Duration.ofMinutes((long) Math.pow(2, retryCount));
    };
}
```

#### (5) 이메일 발송 현황 조회 API (선택)

동아리장이 이메일 발송 현황을 확인할 수 있는 API 추가.

```
GET /api/v1/owner/club/{clubId}/email/status
```

응답: PROCESSING / COMPLETE / FAILED / GIVE_UP 건수 통계

---

## 3. LLM 연동 - 동아리 소개 콘텐츠 생성

### 3.1 현재 상황

- 동아리 소개(`Club.description`)는 `@Lob (LONGTEXT)` 컬럼
- 동아리장이 Toast UI Editor로 `.md` 형식으로 작성
- 별도 가공 없이 DB에 저장 후 그대로 렌더링

### 3.2 목표

동아리장이 작성한 마크다운 초안을 LLM에 입력하여 **더 풍부하고 정제된 소개 문구**로 변환.

### 3.3 API 설계

```
POST /api/v1/owner/club/{clubId}/description/enhance
```

**Request:**
```json
{
  "rawDescription": "# 알고리즘 스터디\n매주 BOJ 문제풀이..."
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "enhancedDescription": "## 알고리즘 스터디\n\n안녕하세요! 저희는 ..."
  }
}
```

`rawDescription`을 LLM에 전달하고, 반환된 결과를 그대로 응답. DB 저장은 동아리장이 확인 후 기존 수정 API(`PUT /{clubId}`)를 통해 진행.

### 3.4 구현 방향

#### (1) Anthropic Claude API 연동

```gradle
// build.gradle 추가
implementation 'com.fasterxml.jackson.core:jackson-databind'  // 이미 있음
// HTTP Client로 Claude API 호출 (RestClient 사용)
```

```java
// LlmDescriptionService.java
@Service
public class LlmDescriptionService {

    private final RestClient restClient;

    @Value("${anthropic.api.key}")
    private String apiKey;

    public String enhance(String rawDescription) {
        String systemPrompt = """
            당신은 대학 동아리 소개 전문 에디터입니다.
            동아리장이 작성한 마크다운 초안을 받아 더 매력적이고 가독성 있는 소개문으로 개선해주세요.
            - 마크다운 형식 유지
            - 어조는 친근하고 활기차게
            - 핵심 정보(활동 내용, 모집 대상, 일정)는 반드시 포함
            - 원문의 의도를 해치지 않도록 유지
            """;

        // Anthropic Messages API 호출
        return callClaudeApi(systemPrompt, rawDescription);
    }
}
```

#### (2) application.properties 설정 추가

```properties
anthropic.api.key=${ANTHROPIC_API_KEY}
anthropic.api.model=claude-haiku-4-5-20251001
anthropic.api.max-tokens=2000
```

#### (3) 비동기 처리 (선택)

LLM 응답이 수 초 걸릴 수 있으므로, SSE(Server-Sent Events) 또는 polling 방식 고려.

```
# 옵션 A: 동기 (단순, 구현 빠름)
POST /enhance → 기다림 → 결과 반환

# 옵션 B: 비동기 polling (UX 좋음)
POST /enhance → 202 Accepted + taskId 반환
GET /enhance/{taskId}/status → PENDING | DONE | ERROR
GET /enhance/{taskId}/result → 완성 결과
```

> **권장**: 옵션 A로 시작, 응답 지연 문제 발생 시 옵션 B로 전환.

#### (4) Rate Limiting / 비용 보호

```java
// 동아리당 하루 최대 N회 호출 제한 (Redis or DB 카운터)
@RateLimiter(name = "llmApi") // Resilience4j 이미 의존성 있음
public String enhance(String rawDescription) { ... }
```

---

## 4. 커스텀 어노테이션 기반 웹훅 알림 및 로그 레벨 정비

### 4.1 현재 상황

- `@DiscordAlert` 어노테이션이 스케줄러에만 적용됨
- 로그 레벨 기준이 불명확 → 중요하지 않은 이벤트에도 알림 발생 가능성
- 치명적 오류(서비스 불능)와 일반 오류가 같은 채널로 혼재
- 운영 중 알람 피로도(Alert Fatigue) 발생 우려

### 4.2 목표

- 로그 레벨을 명확히 정의하고 코드에 일관되게 적용
- Discord 알림 채널을 심각도별로 분리
- 치명적 오류 발생 시 즉각 알림, 일반 오류는 로그로만 기록

### 4.3 로그 레벨 기준 정의

| 레벨 | 사용 기준 | 예시 |
|------|---------|------|
| `ERROR` | 서비스 기능 일부 실패, 즉시 확인 필요 | SMTP 연결 실패, OCI 업로드 실패, 스케줄러 예외 |
| `WARN` | 비정상 상황이지만 서비스 영향 없음 | 이메일 재시도 발생, 유효하지 않은 토큰 접근 |
| `INFO` | 정상 비즈니스 이벤트 기록 | 동아리 등록, 모집 마감, 이메일 발송 완료 |
| `DEBUG` | 개발/디버깅 전용 (prod에서는 비활성) | SQL 쿼리, 파라미터 값, 내부 상태 |

### 4.4 커스텀 어노테이션 개선

#### (1) 심각도 레벨 추가

```java
// @DiscordAlert 어노테이션 개선
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DiscordAlert {
    String value();           // 기능 이름 (기존)
    AlertLevel level() default AlertLevel.ERROR;  // 신규
    boolean onSuccess() default false;            // 성공 시 알림 여부 (신규)
}

public enum AlertLevel {
    CRITICAL,  // 즉각 확인 필요 (서비스 불능 수준)
    ERROR,     // 오류 발생 (기존 기본값)
    WARN       // 경고 (발송하지 않을 수도 있음)
}
```

#### (2) Discord 채널 분리

```java
// DiscordAlertService.java
public void sendErrorAlert(AlertLevel level, String message, Exception ex) {
    String webhookUrl = switch (level) {
        case CRITICAL -> criticalWebhookUrl;   // 별도 채널 (즉각 알림)
        case ERROR    -> errorWebhookUrl;       // 오류 채널
        case WARN     -> warnWebhookUrl;        // 경고 채널 (선택적)
    };
    // ...
}
```

```properties
# application.properties 추가
discord.webhook.critical=${DISCORD_WEBHOOK_CRITICAL}
discord.webhook.error=${DISCORD_WEBHOOK_ERROR}
discord.webhook.warn=${DISCORD_WEBHOOK_WARN}
```

#### (3) 치명적 오류 대상 정의

`CRITICAL` 레벨 적용 대상:
- DB 연결 실패
- OCI 인증 실패
- SMTP 인증 실패 (`SMTP_AUTH_FAILED`)
- EmailRetryQueue GIVE_UP 발생
- 스케줄러 전체 실패

#### (4) AOP 개선 - 예외 타입별 레벨 자동 결정

```java
@AfterThrowing(pointcut = "@annotation(discordAlert)", throwing = "ex")
public void handleException(JoinPoint jp, DiscordAlert discordAlert, Exception ex) {
    AlertLevel effectiveLevel = determineLevel(discordAlert.level(), ex);
    discordAlertService.sendErrorAlert(effectiveLevel, buildMessage(jp, discordAlert, ex), ex);
}

private AlertLevel determineLevel(AlertLevel declared, Exception ex) {
    // 선언된 레벨이 CRITICAL이면 그대로, 아니면 예외 타입으로 격상 판단
    if (ex instanceof MailAuthenticationException) return AlertLevel.CRITICAL;
    if (ex instanceof DataAccessException) return AlertLevel.CRITICAL;
    return declared;
}
```

#### (5) 알람 피로도 감소 전략

- WARN 레벨 알림은 Discord 대신 로그파일에만 기록 (설정으로 토글 가능)
- 같은 오류가 단시간 반복되면 중복 발송 억제 (5분 내 동일 오류 1회만 발송)
- 성공 이벤트 알림은 `onSuccess = true`로 명시적으로 설정한 경우만 발송

---

## 5. thumbnailURL 개선

### 5.1 현재 상황

```java
// OciStorageService.createFinalOciUrl()
"https://objectstorage.{region}.oraclecloud.com/n/{namespace}/b/{bucket}/o/{encodedFileName}"
```

- OCI Object Storage의 Public URL을 직접 클라이언트에 노출
- URL이 변경되면 (버킷 이동, 도메인 변경 등) 기존 저장된 URL 전체 무효화
- 이미지 최적화(리사이징, WebP 변환) 불가
- CDN 미적용으로 글로벌 접근 시 지연 발생 가능
- 파일 접근 권한 제어 불가 (버킷이 Public이면 누구나 직접 접근 가능)

### 5.2 개선 방향

#### (1) fileKey 기반 서빙 URL 추상화 (핵심)

OCI URL 직접 노출 대신, 백엔드를 통해 서빙하거나 CDN URL 기반으로 전환.

**현재:**
```
DB 저장: thumbnailFileKey = "uuid_filename.jpg"
클라이언트에 반환: "https://objectstorage.ap-osaka-1.oraclecloud.com/n/axxgrlwqvcxn/b/smu-club-files/o/uuid_filename.jpg"
```

**개선 후:**
```
DB 저장: thumbnailFileKey = "uuid_filename.jpg"  (변경 없음)
클라이언트에 반환: "https://your-domain.com/files/uuid_filename.jpg"  (or CDN URL)
```

URL 생성 로직을 `OciStorageService`에서 전담하고, 나중에 CDN 도메인만 변경하면 전체 적용.

```java
// OciStorageService.java
public String buildPublicUrl(String fileKey) {
    // 향후 CDN 도메인으로 변경 시 이 메서드만 수정
    return basePublicUrl + "/" + URLEncoder.encode(fileKey, UTF_8);
}
```

```properties
# application.properties
oci.storage.public-base-url=${OCI_PUBLIC_BASE_URL:https://objectstorage.ap-osaka-1.oraclecloud.com/n/axxgrlwqvcxn/b/smu-club-files/o}
```

#### (2) 썸네일 전용 Pre-Signed URL 유효기간 관리

현재 Pre-Signed URL은 업로드용으로만 사용되고 조회는 Public URL 사용.
의도한 동작이면 유지, 버킷 보안 강화 시 조회도 Pre-Signed URL로 전환 가능.

#### (3) 이미지 리사이징 (선택)

썸네일은 특정 사이즈(예: 400×300)로 제한하여 트래픽 절감.

**옵션 A**: 업로드 후 Spring에서 리사이징 (Thumbnailator 라이브러리)
**옵션 B**: OCI 또는 CDN의 이미지 변환 기능 활용

```gradle
// 옵션 A 시
implementation 'net.coobird:thumbnailator:0.4.20'
```

#### (4) 파일 타입 검증 강화

현재 `NotAllowedFileType` 예외가 존재하지만 검증 범위 확인 필요.

```java
// FileUploadService.java
private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
    "image/jpeg", "image/png", "image/gif", "image/webp"
);
private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

public void validateFileType(String contentType, long fileSize) {
    if (!ALLOWED_IMAGE_TYPES.contains(contentType)) {
        throw new NotAllowedFileType(contentType);
    }
    if (fileSize > MAX_FILE_SIZE) {
        throw new FileSizeLimitExceededException();
    }
}
```

#### (5) ACTIVE 상태 파일만 URL 반환

```java
// ClubResponseDto 조회 시
public String getThumbnailUrl() {
    if (thumbnailFileKey == null) return null;
    // FileMetaData 상태 확인 후 반환 (ACTIVE만 허용)
    return ociStorageService.buildPublicUrl(thumbnailFileKey);
}
```

---

## 우선순위 및 작업 순서 (권장)

| 순서 | 주제 | 이유 |
|-----|------|------|
| 1 | **5. thumbnailURL 개선** | 구현 범위 작음, 즉각적인 유지보수성 향상 |
| 2 | **4. 웹훅 알림 및 로그 레벨 정비** | 이후 작업의 모니터링 기반이 됨 |
| 3 | **2. 이메일 시스템 개선** | 기존 기능 안정화, 중간 복잡도 |
| 4 | **3. LLM 연동** | 신규 기능, 독립적으로 개발 가능 |
| 5 | **1. 3-Tier 고가용성** | 인프라 변경 범위가 가장 크고, 위 작업들이 안정화된 후 진행 |

---

## 환경변수 추가 목록

```dotenv
# LLM (주제 3)
ANTHROPIC_API_KEY=

# Discord 채널 분리 (주제 4)
DISCORD_WEBHOOK_CRITICAL=
DISCORD_WEBHOOK_ERROR=
DISCORD_WEBHOOK_WARN=

# OCI 공개 URL 베이스 (주제 5)
OCI_PUBLIC_BASE_URL=

# ShedLock은 기존 DB 연결 사용 (추가 환경변수 없음)
```
# SMU-CLUB 대규모 리팩토링 명세서

> 작성일: 2026-03-13
> 목적: 프로젝트 품질 향상 및 운영 안정성 확보

---

## 현재 스택 요약

| 항목 | 현재 상태 |
|------|----------|
| 백엔드 | Java 21, Spring Boot 3.5.5, JPA, MySQL 8.0 |
| 파일 저장소 | OCI Object Storage |
| 이메일 | OCI SMTP + 지수 백오프 재시도 큐 |
| 로깅 | Log4j2 + Discord Webhook (AOP) |
| 배포 | Docker Compose, OCI Cloud, GitHub Actions |
| REST API | 공개 4개, 회원 11개, 운영진 13개 = 총 28개 |

---

## 리팩토링 주제 목록

1. [3-Tier Architecture 고가용성](#1-3-tier-architecture-고가용성)
2. [동기/비동기 - 이메일 시스템 개선](#2-동기비동기---이메일-시스템-개선)
3. [LLM 연동 - 동아리 소개 콘텐츠 생성](#3-llm-연동---동아리-소개-콘텐츠-생성)
4. [커스텀 어노테이션 기반 웹훅 알림 및 로그 레벨 정비](#4-커스텀-어노테이션-기반-웹훅-알림-및-로그-레벨-정비)
5. [thumbnailURL 개선](#5-thumbnailurl-개선)

---

## 1. 3-Tier Architecture 고가용성

### 1.1 현재 문제점

- 단일 `docker-compose.yml`로 모든 서비스(nginx, backend, db)가 한 인스턴스에서 동작
- 백엔드 인스턴스가 1개여서 배포 시 다운타임 발생
- MySQL도 단일 인스턴스 → 장애 시 서비스 전체 중단
- 스케줄러(이메일 재시도, 모집 마감, 파일 정리)가 백엔드 애플리케이션 내부에 종속

### 1.2 목표 아키텍처

```
[ Internet ]
     ↓
[ Nginx (Reverse Proxy + SSL Termination) ]
     ↓
[ Backend Cluster ]
  ├── backend-1 (Spring Boot)
  └── backend-2 (Spring Boot)
     ↓
[ MySQL (Primary) ]
  └── (선택적) Read Replica
```

### 1.3 개선 방향

#### (1) Nginx 로드밸런싱 설정

현재 `nginx.conf`에서 단일 `backend` 컨테이너를 바라보고 있음.
`upstream` 블록으로 두 개 이상의 백엔드 컨테이너로 로드밸런싱 구성.

```nginx
# nginx/nginx.conf 수정 방향
upstream backend_cluster {
    server backend-1:8080;
    server backend-2:8080;
    keepalive 32;
}

server {
    location /api/ {
        proxy_pass http://backend_cluster;
    }
}
```

#### (2) docker-compose 멀티 인스턴스

```yaml
# docker-compose.yml 수정 방향
services:
  backend-1:
    image: bongguscha/smu-club-backend:latest
    environment:
      SPRING_PROFILES_ACTIVE: prod

  backend-2:
    image: bongguscha/smu-club-backend:latest
    environment:
      SPRING_PROFILES_ACTIVE: prod
```

#### (3) 스케줄러 중복 실행 방지 (ShedLock 도입)

다중 인스턴스 환경에서는 스케줄러가 두 인스턴스에서 동시에 실행됨.
**ShedLock** 라이브러리로 DB 기반 분산 락 적용.

```gradle
// build.gradle 추가
implementation 'net.javacrumbs.shedlock:shedlock-spring:5.10.0'
implementation 'net.javacrumbs.shedlock:shedlock-provider-jdbc-template:5.10.0'
```

```java
// 스케줄러 적용 예시
@Scheduled(cron = "0 * * * * *")
@SchedulerLock(name = "emailRetryScheduler", lockAtMostFor = "50s", lockAtLeastFor = "10s")
public void retryFailedEmails() { ... }

@Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
@SchedulerLock(name = "recruitmentAutoClosureScheduler", lockAtMostFor = "23h")
public void closeExpiredRecruitments() { ... }

@Scheduled(cron = "0 0 3 * * *")
@SchedulerLock(name = "oracleStorageCleanupScheduler", lockAtMostFor = "2h")
public void cleanUpOrphanFiles() { ... }
```

```sql
-- ShedLock 테이블 DDL (MySQL)
CREATE TABLE shedlock (
    name      VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
```

#### (4) Graceful Shutdown 보장

```yaml
# application.properties
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=30s
```

롤링 배포 시 진행 중인 요청이 끊기지 않도록 보장.

#### (5) Health Check 엔드포인트

```java
// Spring Actuator 활성화
management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=when-authorized
```

nginx에서 헬스체크로 비정상 인스턴스 자동 제외.

---

## 2. 동기/비동기 - 이메일 시스템 개선

### 2.1 현재 구조

```
POST /email (동기)
  └─ 권한 검증 + 상태 PROCESSING 변경
       └─ @Async("mailExecutor") → SMTP 발송
            ├── 성공: COMPLETE
            └── 실패: EmailRetryQueue 저장

@Scheduled(0 * * * * *) EmailRetryScheduler
  └─ nextRetryDate 지난 항목 100개씩 재시도 (최대 5회)
```

### 2.2 현재 문제점

- `@Async` 스레드풀 설정이 명시적이지 않아 스레드 고갈 가능성 존재
- EmailRetryQueue 자체가 DB 테이블 → 스케줄러 부하 시 DB I/O 증가
- 재시도 로직이 스케줄러 + 서비스 레이어에 분산되어 있어 가독성 저하
- 이메일 발송 실패 원인 구분 없이 모두 동일하게 재시도 처리
- GIVE_UP 후 관리자 알림 채널 없음

### 2.3 개선 방향

#### (1) 스레드풀 명시적 설정

```java
// AsyncConfig.java
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("mailExecutor")
    public Executor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mail-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

#### (2) 재시도 실패 원인 분류

```java
public enum EmailFailReason {
    SMTP_CONNECTION_FAILED,   // 서버 연결 실패 → 재시도 유효
    INVALID_EMAIL_ADDRESS,    // 이메일 주소 오류 → 재시도 불필요
    SMTP_AUTH_FAILED,         // 인증 실패 → 재시도 불필요, 즉시 관리자 알림
    RATE_LIMIT_EXCEEDED,      // 발송 한도 초과 → 긴 백오프 적용
    UNKNOWN                   // 기타 → 기본 재시도
}
```

`EmailRetryQueue`에 `failReason` 컬럼 추가, 원인에 따라 재시도 여부 결정.

#### (3) GIVE_UP 시 Discord 알림 강화

```java
// EmailRetryService.java
private void markAsGiveUp(EmailRetryQueue task) {
    task.giveUp();
    emailRetryQueueRepository.save(task);

    // 기존 로그만 남기던 것에서 Discord 즉시 알림으로 격상
    discordAlertService.sendGiveUpAlert(task);
}
```

#### (4) 재시도 백오프 전략 개선

현재: `2^retryCount 분` (1회→2분, 2회→4분, 3회→8분, 4회→16분, 5회→GIVE_UP)
개선: `failReason`별 다른 백오프 전략 적용

```java
public Duration calculateNextRetryDelay(EmailFailReason reason, int retryCount) {
    return switch (reason) {
        case SMTP_CONNECTION_FAILED -> Duration.ofMinutes((long) Math.pow(2, retryCount));
        case RATE_LIMIT_EXCEEDED    -> Duration.ofHours(1); // 고정 1시간
        case INVALID_EMAIL_ADDRESS  -> null; // 재시도 안 함
        default                     -> Duration.ofMinutes((long) Math.pow(2, retryCount));
    };
}
```

#### (5) 이메일 발송 현황 조회 API (선택)

동아리장이 이메일 발송 현황을 확인할 수 있는 API 추가.

```
GET /api/v1/owner/club/{clubId}/email/status
```

응답: PROCESSING / COMPLETE / FAILED / GIVE_UP 건수 통계

---

## 3. LLM 연동 - 동아리 소개 콘텐츠 생성

### 3.1 현재 상황

- 동아리 소개(`Club.description`)는 `@Lob (LONGTEXT)` 컬럼
- 동아리장이 Toast UI Editor로 `.md` 형식으로 작성
- 별도 가공 없이 DB에 저장 후 그대로 렌더링

### 3.2 목표

동아리장이 작성한 마크다운 초안을 LLM에 입력하여 **더 풍부하고 정제된 소개 문구**로 변환.

### 3.3 API 설계

```
POST /api/v1/owner/club/{clubId}/description/enhance
```

**Request:**
```json
{
  "rawDescription": "# 알고리즘 스터디\n매주 BOJ 문제풀이..."
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "enhancedDescription": "## 알고리즘 스터디\n\n안녕하세요! 저희는 ..."
  }
}
```

`rawDescription`을 LLM에 전달하고, 반환된 결과를 그대로 응답. DB 저장은 동아리장이 확인 후 기존 수정 API(`PUT /{clubId}`)를 통해 진행.

### 3.4 구현 방향

#### (1) Anthropic Claude API 연동

```gradle
// build.gradle 추가
implementation 'com.fasterxml.jackson.core:jackson-databind'  // 이미 있음
// HTTP Client로 Claude API 호출 (RestClient 사용)
```

```java
// LlmDescriptionService.java
@Service
public class LlmDescriptionService {

    private final RestClient restClient;

    @Value("${anthropic.api.key}")
    private String apiKey;

    public String enhance(String rawDescription) {
        String systemPrompt = """
            당신은 대학 동아리 소개 전문 에디터입니다.
            동아리장이 작성한 마크다운 초안을 받아 더 매력적이고 가독성 있는 소개문으로 개선해주세요.
            - 마크다운 형식 유지
            - 어조는 친근하고 활기차게
            - 핵심 정보(활동 내용, 모집 대상, 일정)는 반드시 포함
            - 원문의 의도를 해치지 않도록 유지
            """;

        // Anthropic Messages API 호출
        return callClaudeApi(systemPrompt, rawDescription);
    }
}
```

#### (2) application.properties 설정 추가

```properties
anthropic.api.key=${ANTHROPIC_API_KEY}
anthropic.api.model=claude-haiku-4-5-20251001
anthropic.api.max-tokens=2000
```

#### (3) 비동기 처리 (선택)

LLM 응답이 수 초 걸릴 수 있으므로, SSE(Server-Sent Events) 또는 polling 방식 고려.

```
# 옵션 A: 동기 (단순, 구현 빠름)
POST /enhance → 기다림 → 결과 반환

# 옵션 B: 비동기 polling (UX 좋음)
POST /enhance → 202 Accepted + taskId 반환
GET /enhance/{taskId}/status → PENDING | DONE | ERROR
GET /enhance/{taskId}/result → 완성 결과
```

> **권장**: 옵션 A로 시작, 응답 지연 문제 발생 시 옵션 B로 전환.

#### (4) Rate Limiting / 비용 보호

```java
// 동아리당 하루 최대 N회 호출 제한 (Redis or DB 카운터)
@RateLimiter(name = "llmApi") // Resilience4j 이미 의존성 있음
public String enhance(String rawDescription) { ... }
```

---

## 4. 커스텀 어노테이션 기반 웹훅 알림 및 로그 레벨 정비

### 4.1 현재 상황

- `@DiscordAlert` 어노테이션이 스케줄러에만 적용됨
- 로그 레벨 기준이 불명확 → 중요하지 않은 이벤트에도 알림 발생 가능성
- 치명적 오류(서비스 불능)와 일반 오류가 같은 채널로 혼재
- 운영 중 알람 피로도(Alert Fatigue) 발생 우려

### 4.2 목표

- 로그 레벨을 명확히 정의하고 코드에 일관되게 적용
- Discord 알림 채널을 심각도별로 분리
- 치명적 오류 발생 시 즉각 알림, 일반 오류는 로그로만 기록

### 4.3 로그 레벨 기준 정의

| 레벨 | 사용 기준 | 예시 |
|------|---------|------|
| `ERROR` | 서비스 기능 일부 실패, 즉시 확인 필요 | SMTP 연결 실패, OCI 업로드 실패, 스케줄러 예외 |
| `WARN` | 비정상 상황이지만 서비스 영향 없음 | 이메일 재시도 발생, 유효하지 않은 토큰 접근 |
| `INFO` | 정상 비즈니스 이벤트 기록 | 동아리 등록, 모집 마감, 이메일 발송 완료 |
| `DEBUG` | 개발/디버깅 전용 (prod에서는 비활성) | SQL 쿼리, 파라미터 값, 내부 상태 |

### 4.4 커스텀 어노테이션 개선

#### (1) 심각도 레벨 추가

```java
// @DiscordAlert 어노테이션 개선
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DiscordAlert {
    String value();           // 기능 이름 (기존)
    AlertLevel level() default AlertLevel.ERROR;  // 신규
    boolean onSuccess() default false;            // 성공 시 알림 여부 (신규)
}

public enum AlertLevel {
    CRITICAL,  // 즉각 확인 필요 (서비스 불능 수준)
    ERROR,     // 오류 발생 (기존 기본값)
    WARN       // 경고 (발송하지 않을 수도 있음)
}
```

#### (2) Discord 채널 분리

```java
// DiscordAlertService.java
public void sendErrorAlert(AlertLevel level, String message, Exception ex) {
    String webhookUrl = switch (level) {
        case CRITICAL -> criticalWebhookUrl;   // 별도 채널 (즉각 알림)
        case ERROR    -> errorWebhookUrl;       // 오류 채널
        case WARN     -> warnWebhookUrl;        // 경고 채널 (선택적)
    };
    // ...
}
```

```properties
# application.properties 추가
discord.webhook.critical=${DISCORD_WEBHOOK_CRITICAL}
discord.webhook.error=${DISCORD_WEBHOOK_ERROR}
discord.webhook.warn=${DISCORD_WEBHOOK_WARN}
```

#### (3) 치명적 오류 대상 정의

`CRITICAL` 레벨 적용 대상:
- DB 연결 실패
- OCI 인증 실패
- SMTP 인증 실패 (`SMTP_AUTH_FAILED`)
- EmailRetryQueue GIVE_UP 발생
- 스케줄러 전체 실패

#### (4) AOP 개선 - 예외 타입별 레벨 자동 결정

```java
@AfterThrowing(pointcut = "@annotation(discordAlert)", throwing = "ex")
public void handleException(JoinPoint jp, DiscordAlert discordAlert, Exception ex) {
    AlertLevel effectiveLevel = determineLevel(discordAlert.level(), ex);
    discordAlertService.sendErrorAlert(effectiveLevel, buildMessage(jp, discordAlert, ex), ex);
}

private AlertLevel determineLevel(AlertLevel declared, Exception ex) {
    // 선언된 레벨이 CRITICAL이면 그대로, 아니면 예외 타입으로 격상 판단
    if (ex instanceof MailAuthenticationException) return AlertLevel.CRITICAL;
    if (ex instanceof DataAccessException) return AlertLevel.CRITICAL;
    return declared;
}
```

#### (5) 알람 피로도 감소 전략

- WARN 레벨 알림은 Discord 대신 로그파일에만 기록 (설정으로 토글 가능)
- 같은 오류가 단시간 반복되면 중복 발송 억제 (5분 내 동일 오류 1회만 발송)
- 성공 이벤트 알림은 `onSuccess = true`로 명시적으로 설정한 경우만 발송

---

## 5. thumbnailURL 개선

### 5.1 현재 상황

```java
// OciStorageService.createFinalOciUrl()
"https://objectstorage.{region}.oraclecloud.com/n/{namespace}/b/{bucket}/o/{encodedFileName}"
```

- OCI Object Storage의 Public URL을 직접 클라이언트에 노출
- URL이 변경되면 (버킷 이동, 도메인 변경 등) 기존 저장된 URL 전체 무효화
- 이미지 최적화(리사이징, WebP 변환) 불가
- CDN 미적용으로 글로벌 접근 시 지연 발생 가능
- 파일 접근 권한 제어 불가 (버킷이 Public이면 누구나 직접 접근 가능)

### 5.2 개선 방향

#### (1) fileKey 기반 서빙 URL 추상화 (핵심)

OCI URL 직접 노출 대신, 백엔드를 통해 서빙하거나 CDN URL 기반으로 전환.

**현재:**
```
DB 저장: thumbnailFileKey = "uuid_filename.jpg"
클라이언트에 반환: "https://objectstorage.ap-osaka-1.oraclecloud.com/n/axxgrlwqvcxn/b/smu-club-files/o/uuid_filename.jpg"
```

**개선 후:**
```
DB 저장: thumbnailFileKey = "uuid_filename.jpg"  (변경 없음)
클라이언트에 반환: "https://your-domain.com/files/uuid_filename.jpg"  (or CDN URL)
```

URL 생성 로직을 `OciStorageService`에서 전담하고, 나중에 CDN 도메인만 변경하면 전체 적용.

```java
// OciStorageService.java
public String buildPublicUrl(String fileKey) {
    // 향후 CDN 도메인으로 변경 시 이 메서드만 수정
    return basePublicUrl + "/" + URLEncoder.encode(fileKey, UTF_8);
}
```

```properties
# application.properties
oci.storage.public-base-url=${OCI_PUBLIC_BASE_URL:https://objectstorage.ap-osaka-1.oraclecloud.com/n/axxgrlwqvcxn/b/smu-club-files/o}
```

#### (2) 썸네일 전용 Pre-Signed URL 유효기간 관리

현재 Pre-Signed URL은 업로드용으로만 사용되고 조회는 Public URL 사용.
의도한 동작이면 유지, 버킷 보안 강화 시 조회도 Pre-Signed URL로 전환 가능.

#### (3) 이미지 리사이징 (선택)

썸네일은 특정 사이즈(예: 400×300)로 제한하여 트래픽 절감.

**옵션 A**: 업로드 후 Spring에서 리사이징 (Thumbnailator 라이브러리)
**옵션 B**: OCI 또는 CDN의 이미지 변환 기능 활용

```gradle
// 옵션 A 시
implementation 'net.coobird:thumbnailator:0.4.20'
```

#### (4) 파일 타입 검증 강화

현재 `NotAllowedFileType` 예외가 존재하지만 검증 범위 확인 필요.

```java
// FileUploadService.java
private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
    "image/jpeg", "image/png", "image/gif", "image/webp"
);
private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

public void validateFileType(String contentType, long fileSize) {
    if (!ALLOWED_IMAGE_TYPES.contains(contentType)) {
        throw new NotAllowedFileType(contentType);
    }
    if (fileSize > MAX_FILE_SIZE) {
        throw new FileSizeLimitExceededException();
    }
}
```

#### (5) ACTIVE 상태 파일만 URL 반환

```java
// ClubResponseDto 조회 시
public String getThumbnailUrl() {
    if (thumbnailFileKey == null) return null;
    // FileMetaData 상태 확인 후 반환 (ACTIVE만 허용)
    return ociStorageService.buildPublicUrl(thumbnailFileKey);
}
```

---

## 우선순위 및 작업 순서 (권장)

| 순서 | 주제 | 이유 |
|-----|------|------|
| 1 | **5. thumbnailURL 개선** | 구현 범위 작음, 즉각적인 유지보수성 향상 |
| 2 | **4. 웹훅 알림 및 로그 레벨 정비** | 이후 작업의 모니터링 기반이 됨 |
| 3 | **2. 이메일 시스템 개선** | 기존 기능 안정화, 중간 복잡도 |
| 4 | **3. LLM 연동** | 신규 기능, 독립적으로 개발 가능 |
| 5 | **1. 3-Tier 고가용성** | 인프라 변경 범위가 가장 크고, 위 작업들이 안정화된 후 진행 |

---

## 환경변수 추가 목록

```dotenv
# LLM (주제 3)
ANTHROPIC_API_KEY=

# Discord 채널 분리 (주제 4)
DISCORD_WEBHOOK_CRITICAL=
DISCORD_WEBHOOK_ERROR=
DISCORD_WEBHOOK_WARN=

# OCI 공개 URL 베이스 (주제 5)
OCI_PUBLIC_BASE_URL=

# ShedLock은 기존 DB 연결 사용 (추가 환경변수 없음)
```
