# SMU-CLUB 대규모 리팩토링 명세서

> 작성일: 2026-03-13
> 목적: 프로젝트 품질 향상 및 운영 안정성 확보 (OCI → AWS 이전 반영, 인증/인가 전면 개편)

---

## 현재 스택 요약

| 항목 | 현재 상태 |
|------|----------|
| 백엔드 | Java 21, Spring Boot 3.5.5, JPA, MySQL 8.0 |
| 파일 저장소 | AWS S3 |
| 이메일 | AWS SES + 지수 백오프 재시도 큐 |
| 로깅 | Log4j2 + Discord Webhook (AOP) |
| 배포 | Docker Compose, AWS EC2, GitHub Actions |
| 인증/인가 | ~~Smunity JWT~~ → 세션 기반 운영자 인증 + 비로그인 지원 |
| REST API | 공개(비로그인 지원 포함) + 운영진(세션) |

---

## 리팩토링 주제 목록

0. [인증/인가 전면 개편 - Smunity 제거](#0-인증인가-전면-개편---smunity-제거)
1. [3-Tier Architecture 고가용성](#1-3-tier-architecture-고가용성)
2. [동기/비동기 - 이메일 시스템 개선](#2-동기비동기---이메일-시스템-개선)
3. [LLM 연동 - 동아리 소개 콘텐츠 생성](#3-llm-연동---동아리-소개-콘텐츠-생성)
4. [커스텀 어노테이션 기반 웹훅 알림 및 로그 레벨 정비](#4-커스텀-어노테이션-기반-웹훅-알림-및-로그-레벨-정비)
5. [thumbnailURL 개선](#5-thumbnailurl-개선)
6. [모니터링 시스템 구축](#6-모니터링-시스템-구축)
7. [부하 테스트 가이드라인](#7-부하-테스트-가이드라인)

---

## 0. 인증/인가 전면 개편 - Smunity 제거

### 0.1 현재 문제점

- 외부 서비스 **Smunity** (`smunity.co.kr/api/v1/auth`)에 로그인이 완전 의존
  - Smunity 서비스 장애 시 로그인 전체 불가 (Circuit Breaker로만 대응)
  - 학번/비밀번호가 외부 API로 전달되는 구조 → 보안 취약
- 지원자(동아리에 지원하는 학생)가 로그인해야만 지원 가능 → 진입 장벽
- JWT Stateless 구조이지만, Refresh Token을 DB에 저장하여 사실상 상태 관리 중

### 0.2 신규 인증 아키텍처 개요

```
[ 운영자(동아리장) ]                [ 지원자(학생) ]
      |                                   |
      | ID/PW 로그인                       | 로그인 없음
      ↓                                   ↓
[ 세션 기반 인증 ]              [ 공개 지원 API (비인증) ]
  JSESSIONID Cookie              지원서 폼에 개인정보 직접 입력
      |
      ↓
[ /api/v1/owner/** ]
  동아리 관리, 지원자 조회,
  이메일 발송, 파일 업로드 등
```

**핵심 변경:**
| 구분 | 기존 | 변경 |
|------|------|------|
| 운영자 인증 | Smunity API + JWT | 세션 기반 (Spring Security Form Login) |
| 운영자 계정 관리 | 학번/비밀번호 → Smunity 인증 | 개발자가 DB에 직접 생성 |
| 지원자 인증 | MEMBER 로그인 필요 | **인증 없음** (비로그인 지원) |
| 토큰 | JWT Access + Refresh | 없음 (세션 쿠키) |
| Member 엔티티 | 학번/학과/학교 정보 포함 | **제거** (Operator + Applicant로 분리) |

---

### 0.3 운영자(동아리장) 세션 인증

#### (1) Operator 엔티티

기존 `Member` 엔티티를 대체하는 단순한 운영자 전용 엔티티.
계정은 개발자가 DB에 직접 INSERT하여 동아리장에게 전달.

```java
@Entity
@Table(name = "operators")
public class Operator {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String loginId;          // 개발자가 지정하는 로그인 ID

    @Column(nullable = false)
    private String password;         // BCrypt 해시

    @Column(nullable = false)
    private String clubName;         // 담당 동아리 명 (표시용)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id")
    private Club club;               // 담당 동아리 (1 Operator : 1 Club)

    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
}
```

> **계정 발급 절차**: 개발자가 SQL로 직접 INSERT → loginId/초기 PW를 동아리장에게 전달.
> 초기 비밀번호는 반드시 BCrypt 해시 후 저장.

```sql
-- 계정 발급 예시 (BCrypt hash는 사전에 생성)
INSERT INTO operators (login_id, password, club_name, club_id, created_at)
VALUES ('algo_club_2026', '$2a$10$...bcrypt_hash...', '알고리즘 스터디', 3, NOW());
```

#### (2) Spring Security 세션 설정

```java
// SecurityConfig.java 전면 변경
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 세션 기반으로 전환 (STATELESS 제거)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .maximumSessions(1)                    // 중복 로그인 1개 제한
                .maxSessionsPreventsLogin(false))      // 새 로그인이 기존 세션 만료

            // CSRF: 세션 기반이므로 활성화 (API 클라이언트가 SPA라면 별도 처리)
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/v1/public/**"))  // 공개 API는 CSRF 제외

            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/public/**").permitAll()   // 비로그인 지원 포함
                .requestMatchers("/api/v1/owner/**").hasRole("OWNER")
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())

            .formLogin(form -> form
                .loginProcessingUrl("/api/v1/auth/login")
                .usernameParameter("loginId")
                .passwordParameter("password")
                .successHandler(operatorLoginSuccessHandler())
                .failureHandler(operatorLoginFailureHandler()))

            .logout(logout -> logout
                .logoutUrl("/api/v1/auth/logout")
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler((req, res, auth) -> res.setStatus(200)));

        return http.build();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

#### (3) OperatorDetailsService

```java
@Service
public class OperatorDetailsService implements UserDetailsService {

    private final OperatorRepository operatorRepository;

    @Override
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        Operator operator = operatorRepository.findByLoginId(loginId)
                .orElseThrow(() -> new UsernameNotFoundException("존재하지 않는 계정: " + loginId));
        return new OperatorPrincipal(operator);
    }
}
```

#### (4) 로그인 응답 포맷

```json
// POST /api/v1/auth/login 성공 시
{
  "success": true,
  "data": {
    "loginId": "algo_club_2026",
    "clubName": "알고리즘 스터디",
    "clubId": 3
  }
}
```

세션 쿠키(`JSESSIONID`)가 `Set-Cookie` 헤더로 자동 발급됨.

---

### 0.4 지원자 비로그인 지원 플로우

#### (1) 변경 전/후 비교

**기존 흐름:**
```
지원자 → 회원가입(Smunity 인증) → 로그인 → JWT 획득
→ POST /api/v1/member/clubs/{clubId}/apply (인증 헤더 필수)
```

**신규 흐름:**
```
지원자 → 동아리 상세 페이지 조회 (비인증)
→ 지원서 작성 (이름, 이메일, 전화번호 등 폼에 직접 입력)
→ POST /api/v1/public/clubs/{clubId}/apply (인증 불필요)
```

#### (2) Application 엔티티 (지원서)

기존 `ClubMember`는 `Member` FK를 통해 지원자 정보를 가져왔으나,
비로그인 구조에서는 지원서 자체에 지원자 정보를 저장.

```java
@Entity
@Table(name = "applications")
public class Application {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 지원자 정보 (비로그인이므로 직접 저장)
    @Column(nullable = false)
    private String applicantName;

    @Column(nullable = false)
    private String applicantEmail;

    @Column(nullable = false)
    private String applicantPhone;

    // 지원 대상
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    // 지원서 내용 (기존 ClubMember 필드 이관)
    @Lob
    private String answers;                     // 지원 항목 응답 (JSON)

    private String resumeFileKey;               // 첨부파일 S3 Key (선택)

    @Enumerated(EnumType.STRING)
    private ApplicationStatus status;           // PENDING, ACCEPTED, REJECTED, GIVE_UP

    @Enumerated(EnumType.STRING)
    private EmailStatus emailStatus;            // 이메일 발송 상태

    private String memo;                        // 운영자 메모
    private int retryCount;

    private LocalDateTime appliedAt;
}
```

> 기존 `ClubMember` 엔티티는 `Application`으로 이름/구조를 변경하거나,
> `member` FK 컬럼을 nullable로 전환한 후 점진적 마이그레이션 가능.

#### (3) 지원 API 변경

```
// 기존
POST /api/v1/member/clubs/{clubId}/apply     (MEMBER 인증 필요)

// 변경
POST /api/v1/public/clubs/{clubId}/apply     (인증 불필요)
```

**Request:**
```json
{
  "applicantName": "홍길동",
  "applicantEmail": "hong@smu.ac.kr",
  "applicantPhone": "010-1234-5678",
  "answers": {
    "motivation": "알고리즘에 관심이 많아서...",
    "experience": "BOJ 1000문제 풀이 경험"
  },
  "resumeFileKey": "uuid_resume.pdf"   // 첨부파일 (선택)
}
```

**중복 지원 방지:** 동일 (email + clubId) 조합으로 중복 지원 차단.

```java
// ApplicationRepository
boolean existsByApplicantEmailAndClubId(String email, Long clubId);
```

#### (4) 파일 업로드 Pre-Signed URL (비인증)

첨부파일 업로드용 Pre-Signed URL 발급도 비인증으로 전환.

```
// 기존
POST /api/v1/member/clubs/application/upload-url   (MEMBER 인증 필요)

// 변경
POST /api/v1/public/clubs/application/upload-url   (인증 불필요)
```

> 업로드 전 파일 타입/크기 검증은 기존 로직 그대로 유지.
> 악용 방지를 위해 Rate Limiting 적용 (IP 기반, Resilience4j).

---

### 0.5 제거 대상 코드/설정

| 제거 대상 | 사유 |
|----------|------|
| `UnivApiClient`, `UnivApiClientImpl` | Smunity 연동 불필요 |
| `JwtTokenProvider`, `JwtAuthenticationFilter` | JWT 제거 |
| `TokenService`, `JwtTokenResponse` | JWT 제거 |
| `Member` 엔티티 (또는 단순화) | Operator + Application으로 대체 |
| `MemberRepository`, `MemberController` | 제거 또는 대체 |
| `PublicAuthController` (login/signup/reissue) | 새 세션 로그인으로 대체 |
| `SecurityContextDebugFilter` | 운영 환경 불필요 |
| `SMUNITY_AUTH_API_URL` 환경변수 | Smunity 제거 |
| `JWT_SECRET`, `jwt.*` 설정 | JWT 제거 |

### 0.6 API 엔드포인트 재편

| 역할 | 경로 | 인증 |
|------|------|------|
| 동아리 목록/상세 | `GET /api/v1/public/clubs/**` | 없음 |
| 비로그인 지원 제출 | `POST /api/v1/public/clubs/{clubId}/apply` | 없음 |
| 첨부파일 업로드 URL | `POST /api/v1/public/clubs/application/upload-url` | 없음 |
| 운영자 로그인 | `POST /api/v1/auth/login` | 없음 (로그인 엔드포인트) |
| 운영자 로그아웃 | `POST /api/v1/auth/logout` | 세션 |
| 동아리 관리 전체 | `/api/v1/owner/**` | 세션 (OWNER) |

> 기존 `/api/v1/member/**` 경로는 전부 제거.
> 마이페이지(지원 목록 조회 등) 기능이 필요하면 추후 별도 검토.

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
[ AWS ALB (Application Load Balancer) ]
     ↓
[ Backend Cluster (EC2 or ECS) ]
  ├── backend-1 (Spring Boot)
  └── backend-2 (Spring Boot)
     ↓
[ AWS RDS for MySQL (Multi-AZ 옵션) ]
  └── (선택적) Read Replica
```

> Nginx는 단순 리버스 프록시 역할만 하거나, ALB가 SSL Termination을 담당하면 제거 가능.

### 1.3 개선 방향

#### (1) ALB 기반 로드밸런싱

AWS ALB에서 EC2 Target Group으로 라운드로빈 분산.
기존 Nginx 로드밸런싱 설정은 ALB로 대체하거나, EC2 내부 Nginx는 단순 프록시로만 유지.

ALB를 사용하는 경우 Nginx `upstream` 블록 불필요 — ALB가 `/api/**` 트래픽을 두 인스턴스로 분산.

Nginx를 계속 쓰는 경우:

```nginx
# nginx/nginx.conf 수정 방향 (ALB 미사용 시)
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
@SchedulerLock(name = "s3StorageCleanupScheduler", lockAtMostFor = "2h")
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

ALB 또는 Nginx에서 헬스체크로 비정상 인스턴스 자동 제외.

---

## 2. 동기/비동기 - 이메일 시스템 개선

### 2.1 현재 구조

```
POST /email (동기)
  └─ 권한 검증 + 상태 PROCESSING 변경
       └─ @Async("mailExecutor") → AWS SES 발송
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

### 2.3 AWS SES 전환

기존 OCI SMTP에서 **AWS SES SDK(v2)** 기반으로 전환.

#### 의존성 추가

```gradle
// build.gradle
implementation platform('software.amazon.awssdk:bom:2.25.0')
implementation 'software.amazon.awssdk:ses'
implementation 'software.amazon.awssdk:auth'
```

#### SES 클라이언트 빈 등록

```java
// AwsSesConfig.java
@Configuration
public class AwsSesConfig {

    @Value("${aws.region}")
    private String region;

    @Bean
    public SesV2Client sesV2Client() {
        return SesV2Client.builder()
                .region(Region.of(region))
                // EC2 인스턴스 프로파일 또는 환경변수로 자동 인증
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
```

#### SES 발송 서비스

```java
// AwsSesEmailService.java
@Service
public class AwsSesEmailService {

    private final SesV2Client sesV2Client;

    @Value("${aws.ses.from-address}")
    private String fromAddress;

    public void send(String to, String subject, String htmlBody) {
        SendEmailRequest request = SendEmailRequest.builder()
                .fromEmailAddress(fromAddress)
                .destination(Destination.builder().toAddresses(to).build())
                .content(EmailContent.builder()
                        .simple(Message.builder()
                                .subject(Content.builder().data(subject).charset("UTF-8").build())
                                .body(Body.builder()
                                        .html(Content.builder().data(htmlBody).charset("UTF-8").build())
                                        .build())
                                .build())
                        .build())
                .build();
        sesV2Client.sendEmail(request);
    }
}
```

### 2.4 개선 방향

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
    SES_CONNECTION_FAILED,    // SES 서비스 연결 실패 → 재시도 유효
    INVALID_EMAIL_ADDRESS,    // 이메일 주소 오류 → 재시도 불필요
    SES_AUTH_FAILED,          // AWS 자격증명 실패 → 재시도 불필요, 즉시 관리자 알림
    RATE_LIMIT_EXCEEDED,      // SES 발송 한도 초과 → 긴 백오프 적용
    SUPPRESSION_LIST,         // SES 억제 목록(수신 거부/반송) → 재시도 불필요
    UNKNOWN                   // 기타 → 기본 재시도
}
```

`EmailRetryQueue`에 `failReason` 컬럼 추가, 원인에 따라 재시도 여부 결정.

> AWS SES는 반송(bounce) 및 수신 거부(complaint) 이벤트를 SNS로 전달 가능.
> 향후 SNS → SQS 연동으로 `SUPPRESSION_LIST` 자동 처리 고도화 가능.

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
        case SES_CONNECTION_FAILED -> Duration.ofMinutes((long) Math.pow(2, retryCount));
        case RATE_LIMIT_EXCEEDED   -> Duration.ofHours(1); // 고정 1시간
        case INVALID_EMAIL_ADDRESS,
             SUPPRESSION_LIST      -> null; // 재시도 안 함
        default                    -> Duration.ofMinutes((long) Math.pow(2, retryCount));
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
| `ERROR` | 서비스 기능 일부 실패, 즉시 확인 필요 | SES 연결 실패, S3 업로드 실패, 스케줄러 예외 |
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
- AWS 자격증명(Access Key) 실패
- SES 인증 실패 (`SES_AUTH_FAILED`)
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
    if (ex instanceof SesException se && se.awsErrorDetails().errorCode().equals("InvalidClientTokenId"))
        return AlertLevel.CRITICAL;
    if (ex instanceof S3Exception s3e && s3e.statusCode() == 403)
        return AlertLevel.CRITICAL;
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
// AwsS3Service.buildPublicUrl()
"https://{bucket}.s3.{region}.amazonaws.com/{encodedFileName}"
```

- S3 Public URL을 직접 클라이언트에 노출
- URL이 변경되면 (버킷 이동, 도메인 변경 등) 기존 저장된 URL 전체 무효화
- 이미지 최적화(리사이징, WebP 변환) 불가
- CDN 미적용으로 글로벌 접근 시 지연 발생 가능
- S3 버킷 퍼블릭 접근 제어 정책 변경 시 전체 URL 무효화

### 5.2 AWS 전환 내용

#### S3 클라이언트 설정

```gradle
// build.gradle
implementation platform('software.amazon.awssdk:bom:2.25.0')
implementation 'software.amazon.awssdk:s3'
implementation 'software.amazon.awssdk:auth'
```

```java
// AwsS3Config.java
@Configuration
public class AwsS3Config {

    @Value("${aws.region}")
    private String region;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
```

#### Pre-Signed URL 업로드 발급

```java
// AwsS3Service.java
public String generatePresignedUploadUrl(String fileKey, String contentType) {
    PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(10))
            .putObjectRequest(r -> r
                    .bucket(bucketName)
                    .key(fileKey)
                    .contentType(contentType))
            .build();
    return s3Presigner.presignPutObject(presignRequest).url().toString();
}
```

### 5.3 개선 방향

#### (1) fileKey 기반 서빙 URL 추상화 (핵심)

S3 URL 직접 노출 대신, CloudFront CDN URL 또는 백엔드 프록시 URL로 전환.

**현재:**
```
DB 저장: thumbnailFileKey = "uuid_filename.jpg"
클라이언트에 반환: "https://{bucket}.s3.{region}.amazonaws.com/uuid_filename.jpg"
```

**개선 후:**
```
DB 저장: thumbnailFileKey = "uuid_filename.jpg"  (변경 없음)
클라이언트에 반환: "https://cdn.your-domain.com/uuid_filename.jpg"  (CloudFront URL)
```

URL 생성 로직을 `AwsS3Service`에서 전담하고, CDN 도메인만 변경하면 전체 적용.

```java
// AwsS3Service.java
public String buildPublicUrl(String fileKey) {
    // 향후 CloudFront 도메인으로 변경 시 이 메서드만 수정
    return basePublicUrl + "/" + URLEncoder.encode(fileKey, UTF_8);
}
```

```properties
# application.properties
aws.s3.bucket=${AWS_S3_BUCKET}
aws.s3.public-base-url=${AWS_S3_PUBLIC_BASE_URL}
# CloudFront 사용 시: https://xxxx.cloudfront.net
# S3 직접 사용 시: https://{bucket}.s3.{region}.amazonaws.com
```

#### (2) CloudFront CDN 연동 (선택)

- S3 버킷을 Private으로 설정하고 CloudFront OAC(Origin Access Control)로만 접근 허용
- CloudFront에서 캐싱 + 글로벌 엣지 서빙
- `AWS_S3_PUBLIC_BASE_URL`을 CloudFront 도메인으로 설정하면 코드 변경 없이 전환 완료

#### (3) 이미지 리사이징 (선택)

썸네일은 특정 사이즈(예: 400×300)로 제한하여 트래픽 절감.

**옵션 A**: 업로드 후 Spring에서 리사이징 (Thumbnailator 라이브러리)
**옵션 B**: CloudFront + Lambda@Edge로 온디맨드 리사이징

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
    return awsS3Service.buildPublicUrl(thumbnailFileKey);
}
```

---

---

## 6. 모니터링 시스템 구축

### 6.1 옵션 비교 (최소 비용 기준)

| 옵션 | 월 비용 | 장점 | 단점 | 권장 대상 |
|------|--------|------|------|----------|
| **Prometheus + Grafana** (자체 호스팅) | ~$0 (기존 EC2 활용) 또는 t3.micro $8 | 완전 무료, 커스터마이징 자유도 최고, Spring Actuator 연동 공식 지원 | 초기 설정 복잡, 직접 운영 필요 | **권장** (이 프로젝트) |
| **AWS CloudWatch** | 기본 메트릭 무료, 커스텀 메트릭 $0.30/개/월 | AWS 네이티브, 추가 인프라 없음, EC2/RDS 기본 지표 자동 수집 | 대시보드 불편, 비용이 서서히 쌓임, APM 없음 | 초기 MVP 단계 |
| **Datadog** | $15~$23/호스트/월 | APM·로그·메트릭 통합, 설정 쉬움 | 비용 높음, 학습 곡선 있음 | 팀 규모가 클 때 |
| **Sentry** | 무료 티어 5,000 이벤트/월 | 에러 트래킹 전문, 스택트레이스 수집 우수 | 메트릭/대시보드 없음 | 에러 보조 도구로 병행 가능 |
| **Grafana Cloud** (무료 티어) | 무료 (3 users, 14일 보존) | 자체 호스팅 없이 Grafana 사용 가능 | 보존 기간 짧음, 데이터 업로드 방식 | 서버 리소스 아낄 때 |

> **결론**: Prometheus + Grafana를 기존 EC2의 `docker-compose.yml`에 서비스로 추가하는 것이 **추가 비용 0원**으로 가장 합리적.
> 에러 트래킹은 **Sentry 무료 티어**를 병행하면 커버리지 향상.

---

### 6.2 Prometheus + Grafana 구성

#### (1) 아키텍처

```
[ Spring Boot ]
  └─ /actuator/prometheus  ← Micrometer가 메트릭 노출
       ↑ scrape (15s)
[ Prometheus ]
       ↓ query
[ Grafana ] ← 대시보드 시각화
```

#### (2) 의존성 추가

```gradle
// build.gradle
implementation 'org.springframework.boot:spring-boot-starter-actuator'
implementation 'io.micrometer:micrometer-registry-prometheus'
```

#### (3) application.properties 설정

```properties
# Prometheus 엔드포인트 노출
management.endpoints.web.exposure.include=health,info,prometheus,metrics
management.endpoint.prometheus.enabled=true
management.metrics.export.prometheus.enabled=true

# 애플리케이션 식별 태그 (Grafana 필터링용)
management.metrics.tags.application=${spring.application.name}
management.metrics.tags.env=prod

# JVM, HTTP, DB 커넥션 풀 메트릭 활성화
management.metrics.enable.jvm=true
management.metrics.enable.hikaricp=true
management.metrics.enable.http.server.requests=true
```

#### (4) docker-compose.yml에 서비스 추가

```yaml
services:
  # ... 기존 backend, nginx 서비스 ...

  prometheus:
    image: prom/prometheus:latest
    volumes:
      - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus_data:/prometheus
    ports:
      - "9090:9090"       # 외부 노출 주의: 방화벽/보안 그룹으로 차단 권장
    restart: unless-stopped

  grafana:
    image: grafana/grafana:latest
    environment:
      GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD}
      GF_USERS_ALLOW_SIGN_UP: "false"
    volumes:
      - grafana_data:/var/lib/grafana
    ports:
      - "3000:3000"
    depends_on:
      - prometheus
    restart: unless-stopped

volumes:
  prometheus_data:
  grafana_data:
```

#### (5) prometheus.yml 스크랩 설정

```yaml
# monitoring/prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'smu-club-backend'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets:
          - 'backend-1:8080'
          - 'backend-2:8080'    # 멀티 인스턴스 시

  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']
```

---

### 6.3 Grafana 대시보드 설정

#### 추천 대시보드 (Grafana 공식 ID로 Import)

| 대시보드 | ID | 내용 |
|---------|-----|------|
| JVM Micrometer | `4701` | Heap/Non-Heap, GC, 스레드 상태 |
| Spring Boot Statistics | `6756` | HTTP 요청 수, 응답시간, 오류율 |
| HikariCP | `14091` | DB 커넥션 풀 사용률, 대기 시간 |

> Grafana → Dashboards → Import → 위 ID 입력으로 즉시 사용 가능.

#### 핵심 모니터링 지표

```
# HTTP 요청 수 (PromQL)
rate(http_server_requests_seconds_count{application="smu-club"}[1m])

# P99 응답시간
histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m]))

# 오류율
rate(http_server_requests_seconds_count{status=~"5.."}[1m])
  / rate(http_server_requests_seconds_count[1m])

# JVM Heap 사용률
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}

# DB 커넥션 풀 대기
hikaricp_connections_pending{pool="HikariPool-1"}
```

#### 알람 규칙 예시 (Grafana Alerting)

| 알람 | 조건 | 채널 |
|------|------|------|
| P99 응답 > 2초 | 1분 지속 | Discord CRITICAL |
| 오류율 > 5% | 2분 지속 | Discord ERROR |
| Heap 사용 > 85% | 3분 지속 | Discord WARN |
| DB 커넥션 대기 > 5 | 1분 지속 | Discord ERROR |

---

### 6.4 Sentry 에러 트래킹 (보조 도구, 선택)

```gradle
// build.gradle
implementation 'io.sentry:sentry-spring-boot-starter-jakarta:7.+'
```

```properties
# application.properties
sentry.dsn=${SENTRY_DSN}
sentry.environment=prod
sentry.traces-sample-rate=0.1   # 트랜잭션 10%만 샘플링 (무료 티어 한도 보호)
```

- 예외 발생 시 스택트레이스 + 컨텍스트 자동 수집
- 기존 Discord 알림의 "어디서 터졌는지"를 Sentry가 보완

---

## 7. 부하 테스트 가이드라인

### 7.1 nGrinder vs JMeter 비교

| 항목 | nGrinder | JMeter |
|------|----------|--------|
| 개발사 | 네이버 오픈소스 | Apache 오픈소스 |
| 스크립트 언어 | Groovy / Jython | XML (GUI로 작성) |
| 인터페이스 | 웹 UI (Controller 서버 필요) | 데스크탑 GUI |
| 분산 테스트 | Controller + Agent 구조로 쉬움 | Manager + Worker, 설정 복잡 |
| 학습 곡선 | 스크립트 작성 필요, 진입장벽 있음 | GUI로 클릭만으로 시나리오 작성 가능 |
| 실시간 모니터링 | 웹 UI에서 실시간 그래프 | 플러그인 필요 |
| 리포트 | 웹에서 자동 생성 | 별도 저장 필요 |
| 로컬 실행 | Controller 서버 별도 필요 | 단독 실행 가능 |

> **초보자 권장**: JMeter (GUI 기반, 코드 없이 시나리오 구성 가능)
> **팀 협업/분산 테스트 권장**: nGrinder (웹 UI로 결과 공유 쉬움)
>
> 이 가이드는 **JMeter 기준**으로 상세 설명 (입문용). nGrinder는 7.5에서 보완 설명.

---

### 7.2 부하 테스트 핵심 개념

테스트 전에 반드시 이해해야 할 지표:

| 용어 | 설명 | 목표값 (참고) |
|------|------|-------------|
| **Throughput (TPS)** | 초당 처리 요청 수 | 높을수록 좋음 |
| **Response Time** | 요청~응답 소요 시간 | P95 < 1s, P99 < 2s |
| **Error Rate** | 실패 요청 비율 | < 1% |
| **Concurrent Users** | 동시 접속자 수 | 시나리오에 따라 설정 |
| **Ramp-up** | 가상 유저를 서서히 늘리는 시간 | 갑작스러운 부하 방지 |
| **Think Time** | 요청 사이 대기 시간 | 실제 사용 패턴 모사 |

**테스트 종류:**

```
① Smoke Test    : 최소 부하(1~2 users)로 기본 동작 확인
② Load Test     : 예상 정상 부하에서 지속 실행 (10~30분)
③ Stress Test   : 임계점을 찾기 위해 점진적 부하 증가
④ Soak Test     : 낮은 부하로 장시간(수 시간) 실행 → 메모리 누수 탐지
```

---

### 7.3 JMeter 설치 및 기본 설정

#### 설치

```bash
# macOS
brew install jmeter

# 또는 공식 사이트에서 .zip 다운로드 후
# bin/jmeter (macOS/Linux) 또는 bin/jmeter.bat (Windows) 실행
```

#### 플러그인 설치 (권장)

1. JMeter Plugins Manager 다운로드 → `lib/ext/` 폴더에 복사
2. JMeter 재시작 후 Options → Plugins Manager에서 설치:
   - `3 Basic Graphs` (Throughput, Response Time, Errors 실시간 그래프)
   - `Custom Thread Groups` (Stepping Thread Group - 단계적 부하 증가)

---

### 7.4 JMeter 시나리오 작성 (SMU-CLUB 기준)

#### 시나리오 구조

```
Test Plan
  └─ Thread Group (가상 유저)
       ├─ HTTP Request Defaults (공통 Host/Port 설정)
       ├─ HTTP Cookie Manager   (세션 쿠키 자동 관리)
       ├─ HTTP Header Manager   (Content-Type 등)
       │
       ├─ [시나리오 A] 동아리 목록 조회 (비인증)
       │    └─ HTTP Request: GET /api/v1/public/clubs
       │
       ├─ [시나리오 B] 비로그인 지원서 제출
       │    ├─ HTTP Request: GET /api/v1/public/clubs/{clubId}
       │    └─ HTTP Request: POST /api/v1/public/clubs/{clubId}/apply
       │
       └─ [시나리오 C] 운영자 로그인 + 지원자 목록 조회
            ├─ HTTP Request: POST /api/v1/auth/login
            └─ HTTP Request: GET /api/v1/owner/club/{clubId}/applicants
```

#### Step 1 - Test Plan 생성

1. JMeter 실행 → `File → New`
2. Test Plan 이름 입력: `SMU-CLUB Load Test`

#### Step 2 - Thread Group 추가

`Test Plan` 우클릭 → `Add → Threads → Thread Group`

| 설정 항목 | 값 | 의미 |
|----------|-----|------|
| Number of Threads | `50` | 가상 사용자 50명 |
| Ramp-up Period | `30` | 30초에 걸쳐 50명 투입 |
| Loop Count | `100` | 각 유저가 100회 반복 |

#### Step 3 - HTTP Request Defaults 추가

`Thread Group` 우클릭 → `Add → Config Element → HTTP Request Defaults`

```
Protocol: http
Server Name: localhost   (또는 EC2 IP)
Port: 8080
```

#### Step 4 - 시나리오 A: 동아리 목록 조회

`Thread Group` 우클릭 → `Add → Sampler → HTTP Request`

```
Method: GET
Path: /api/v1/public/clubs
```

#### Step 5 - 시나리오 B: 비로그인 지원서 제출

**HTTP Header Manager 추가** (Thread Group 우클릭 → Config Element):
```
Name: Content-Type
Value: application/json
```

**HTTP Request (지원서 제출)**:
```
Method: POST
Path: /api/v1/public/clubs/1/apply
Body Data:
{
  "applicantName": "테스트유저",
  "applicantEmail": "test${__threadNum}@smu.ac.kr",
  "applicantPhone": "010-0000-${__Random(1000,9999)}",
  "answers": {"motivation": "부하 테스트 중입니다"}
}
```

> `${__threadNum}` : 스레드 번호로 이메일 중복 방지
> `${__Random(1000,9999)}` : 랜덤 숫자 생성 함수

#### Step 6 - 시나리오 C: 운영자 인증 세션

**HTTP Cookie Manager 추가** (Thread Group 우클릭 → Config Element):
- 쿠키 자동 관리 → 로그인 후 JSESSIONID 자동 유지

**HTTP Request (로그인)**:
```
Method: POST
Path: /api/v1/auth/login
Parameters:
  loginId: algo_club_2026
  password: testpassword
```

**HTTP Request (지원자 목록)**:
```
Method: GET
Path: /api/v1/owner/club/1/applicants
```

#### Step 7 - 리스너 추가 (결과 수집)

`Thread Group` 우클릭 → `Add → Listener`:

| 리스너 | 용도 |
|--------|------|
| `View Results Tree` | 개별 요청 상세 (Smoke Test용, 부하 시 비활성화) |
| `Summary Report` | TPS, 평균/최대 응답시간, 오류율 요약 |
| `Aggregate Report` | 백분위수(P90, P95, P99) 확인 |
| `jp@gc - Active Threads Over Time` | 동시 접속자 추이 그래프 (플러그인) |
| `jp@gc - Transactions per Second` | TPS 실시간 그래프 (플러그인) |

---

### 7.5 테스트 단계별 실행 전략

#### Phase 1: Smoke Test (개발 초기)

```
Thread: 1~2명 / Ramp-up: 1s / Duration: 1분
목적: API가 기본적으로 작동하는지 확인
Pass 기준: 오류율 0%, 응답 정상
```

#### Phase 2: Load Test (기능 완성 후)

```
Thread: 30~50명 / Ramp-up: 30s / Duration: 10분
목적: 예상 정상 트래픽에서 안정성 확인
Pass 기준: 오류율 < 1%, P99 응답 < 2초
```

```
# Stepping Thread Group 사용 시 (플러그인)
Start: 0 users
Add: 10 users every 1 minute → 최대 50users
Hold for: 5 minutes
```

#### Phase 3: Stress Test (배포 전)

```
Thread: 10 → 20 → 50 → 100 → 200 단계적 증가
Ramp-up: 각 단계 2분 유지
목적: 시스템 한계점(Breaking Point) 탐색
관찰: TPS가 증가하다 꺾이는 지점 = 실제 처리 한계
```

#### Phase 4: Soak Test (메모리 누수 확인)

```
Thread: 10명 (낮은 부하)
Duration: 2~4시간
목적: Heap 메모리가 지속 증가하는지 확인
관찰: Grafana JVM 대시보드에서 Heap 추이 모니터링
```

---

### 7.6 nGrinder 간략 가이드

#### 구성

```
[ nGrinder Controller ] ← 웹 UI, 테스트 관리, 결과 저장
[ nGrinder Agent ]      ← 실제 부하 발생 (Controller가 명령)
```

#### Docker로 빠른 실행

```bash
# Controller
docker run -d -p 8300:80 -p 16001:16001 -p 12000-12009:12000-12009 \
  --name ngrinder-controller ngrinder/controller

# Agent (Controller와 같은 네트워크)
docker run -d --name ngrinder-agent \
  --link ngrinder-controller:controller \
  ngrinder/agent
```

`http://localhost:8300` 접속 (admin/admin)

#### Groovy 스크립트 예시

```groovy
// nGrinder Script - 동아리 목록 조회
@RunWith(GrinderRunner)
class TestRunner {

    public static GTest test1
    public static HTTPRequest request
    public static Map<String, String> headers = [:]

    @BeforeProcess
    public static void beforeProcess() {
        test1 = new GTest(1, "GET /public/clubs")
        request = new HTTPRequest()
        headers["Content-Type"] = "application/json"
    }

    @Test
    public void test() {
        HTTPResponse response = request.GET(
            "http://localhost:8080/api/v1/public/clubs", headers)

        if (response.statusCode != 200) {
            grinder.logger.warn("FAILED: ${response.statusCode}")
        }
        assertThat(response.statusCode, is(200))
    }
}
```

---

### 7.7 결과 분석 체크리스트

테스트 완료 후 확인할 사항:

```
✅ Summary Report
  - Error % < 1%
  - Average < 500ms
  - 99th Percentile < 2000ms
  - Throughput (TPS) 목표치 달성 여부

✅ Grafana JVM 대시보드
  - GC Pause Time 급증 없는지
  - Heap 사용량 안정적인지 (Soak Test 중 지속 증가 = 메모리 누수)
  - Thread 수 급증 없는지

✅ Grafana HikariCP 대시보드
  - Connection Pending > 0 지속 = DB 커넥션 풀 부족
    → spring.datasource.hikari.maximum-pool-size 조정

✅ 서버 리소스 (CloudWatch 또는 top 명령)
  - CPU 사용률 > 80% 지속 = 스케일 아웃 필요
  - Memory 사용률 > 85% = 힙 사이즈 또는 인스턴스 스펙 조정

✅ 애플리케이션 로그
  - DB 커넥션 타임아웃 에러
  - Thread Pool Rejection (CallerRunsPolicy 발동 여부)
  - SES 발송 실패율 증가 여부
```

---

### 7.8 JMeter CLI 실행 (CI/CD 연동 시)

GUI 대신 CLI로 실행하면 GitHub Actions에서 자동화 가능.

```bash
# 테스트 실행 + 결과 저장
jmeter -n \
  -t test-plans/load-test.jmx \
  -l results/result.jtl \
  -e -o results/html-report

# -n: non-GUI 모드
# -t: JMX 파일 경로
# -l: 결과 JTL 파일
# -e -o: HTML 리포트 생성
```

HTML 리포트(`results/html-report/index.html`)에서 시각적 결과 확인 가능.

---

## 우선순위 및 작업 순서 (권장)

| 순서 | 주제 | 이유 |
|-----|------|------|
| 1 | **0. 인증/인가 개편** | 전체 API 구조의 전제 조건. 이 작업 전에 다른 작업 시작 불가 |
| 2 | **6. 모니터링 구축** | 이후 모든 작업의 관찰 기반. 부하 테스트 전 필수 |
| 3 | **5. thumbnailURL 개선** | 인증 개편 후 S3 연동 구조 확립, 구현 범위 작음 |
| 4 | **4. 웹훅 알림 및 로그 레벨 정비** | 모니터링과 연계하여 알람 체계 완성 |
| 5 | **7. 부하 테스트** | 기능 안정화 후 성능 수치 측정 |
| 6 | **2. 이메일 시스템 개선** | 기존 기능 안정화, 중간 복잡도 |
| 7 | **3. LLM 연동** | 신규 기능, 독립적으로 개발 가능 |
| 8 | **1. 3-Tier 고가용성** | 인프라 변경 범위가 가장 크고, 위 작업들이 안정화된 후 진행 |

---

## 환경변수 추가 목록

```dotenv
# ── 제거 대상 ──────────────────────────────────────────
# SMUNITY_AUTH_API_URL   (Smunity 제거)
# JWT_SECRET             (JWT 제거)

# ── 신규 추가 (주제 0) ──────────────────────────────────
# 세션 설정 (application.properties에서 직접 지정 가능, 필요 시 환경변수화)
# spring.session.timeout=3600   # 세션 만료 시간(초)

# ── AWS 공통 ───────────────────────────────────────────
AWS_REGION=ap-northeast-2
AWS_ACCESS_KEY_ID=          # EC2 인스턴스 프로파일 사용 시 불필요
AWS_SECRET_ACCESS_KEY=      # EC2 인스턴스 프로파일 사용 시 불필요

# ── AWS S3 (주제 5) ────────────────────────────────────
AWS_S3_BUCKET=
AWS_S3_PUBLIC_BASE_URL=     # S3 URL 또는 CloudFront 도메인

# ── AWS SES (주제 2) ───────────────────────────────────
AWS_SES_FROM_ADDRESS=

# ── LLM (주제 3) ───────────────────────────────────────
ANTHROPIC_API_KEY=

# ── Discord 채널 분리 (주제 4) ─────────────────────────
DISCORD_WEBHOOK_CRITICAL=
DISCORD_WEBHOOK_ERROR=
DISCORD_WEBHOOK_WARN=

# ShedLock은 기존 DB 연결 사용 (추가 환경변수 없음)
```

> **EC2 배포 권장**: IAM Role을 EC2 인스턴스 프로파일에 연결하면 `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` 환경변수 없이도 `DefaultCredentialsProvider`가 자동으로 인증. S3, SES에 필요한 최소 권한만 부여할 것.
