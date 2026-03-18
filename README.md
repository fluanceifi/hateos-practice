# HATEOAS 동아리 목록/상세 적용 가이드

> **대상 엔드포인트**
> - `GET /api/v1/public/clubs` — 동아리 전체 목록
> - `GET /api/v1/public/clubs/{clubId}` — 동아리 상세

---

## 왜 이 두 엔드포인트인가

`ClubsResponseDto`에는 `recruitingStatus`와 인증 여부 두 가지 조건이 있다.
서버가 상태에 따라 `_links`를 다르게 내려주면, **클라이언트가 별도 분기 없이 링크 존재 여부로 버튼 렌더링을 결정**할 수 있다.

```
// OPEN + 로그인 O
{
  "id": 1,
  "name": "개발동아리",
  "recruitingStatus": "OPEN",
  "_links": {
    "self":  { "href": "/api/v1/public/clubs/1" },
    "apply": { "href": "/api/v1/member/clubs/1/applications" },
    "list":  { "href": "/api/v1/public/clubs" }
  }
}

// OPEN + 로그인 X
{
  "id": 1,
  "name": "개발동아리",
  "recruitingStatus": "OPEN",
  "_links": {
    "self":  { "href": "/api/v1/public/clubs/1" },
    "login": { "href": "/api/v1/public/auth/login" },
    "list":  { "href": "/api/v1/public/clubs" }
  }
}

// CLOSED (로그인 여부 무관)
{
  "id": 2,
  "name": "음악동아리",
  "recruitingStatus": "CLOSED",
  "_links": {
    "self": { "href": "/api/v1/public/clubs/2" },
    "list": { "href": "/api/v1/public/clubs" }
  }
}
```

---

## 1단계 — 의존성 추가

`build.gradle`

```groovy
implementation 'org.springframework.boot:spring-boot-starter-hateoas'
```

Spring Boot의 `spring-boot-starter-hateoas`는 버전 관리가 BOM에 포함되어 있으므로 버전 명시 불필요.

---

## 2단계 — DTO 변경: `RepresentationModel` 상속

### `ClubsResponseDto` (목록용)

현재:
```java
public class ClubsResponseDto {
    private Long id;
    private String name;
    private String title;
    private RecruitingStatus recruitingStatus;
    private LocalDateTime createdAt;
    private String thumbnailUrl;
}
```

변경 후:
```java
import org.springframework.hateoas.RepresentationModel;

public class ClubsResponseDto extends RepresentationModel<ClubsResponseDto> {
    // 기존 필드 그대로 유지
    private Long id;
    private String name;
    private String title;
    private RecruitingStatus recruitingStatus;
    private LocalDateTime createdAt;
    private String thumbnailUrl;
}
```

> `RepresentationModel`을 상속하면 `_links` 필드가 자동으로 JSON에 포함된다.
> 기존 필드는 수정하지 않아도 된다.

### `ClubResponseDto` (상세용)

동일하게 `extends RepresentationModel<ClubResponseDto>` 추가.
`@Builder` 사용 중이므로 부모 클래스 생성자 충돌 주의 → 아래 참고.

```java
import org.springframework.hateoas.RepresentationModel;

@Builder
@Getter
@AllArgsConstructor
public class ClubResponseDto extends RepresentationModel<ClubResponseDto> {
    // 기존 필드 그대로 유지
    private Long id;
    private String name;
    // ...
}
```

> `@AllArgsConstructor`가 있으면 Lombok이 부모 클래스 필드를 포함하지 않아 문제가 발생할 수 있다.
> 이 경우 `@AllArgsConstructor`를 제거하고 `@Builder`만 유지하거나, `@SuperBuilder`를 사용한다.
> 가장 간단한 해결은 `@AllArgsConstructor` 대신 직접 생성자를 작성하지 않고 `@Builder`만 쓰는 것.

---

## 3단계 — Assembler 클래스 작성 (목록용만)

링크 추가 로직을 컨트롤러에 직접 쓰지 않고, **Assembler** 클래스로 분리한다.
상세 엔드포인트는 단일 객체라 컨트롤러에 인라인으로 처리한다 (아래 4단계 참고).

### 인증 여부 확인 유틸 메서드

Assembler와 컨트롤러에서 공통으로 사용할 로직:

```java
private boolean isLoggedIn() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null
        && auth.isAuthenticated()
        && !(auth instanceof AnonymousAuthenticationToken);
}
```

### `ClubsRepresentationAssembler`

```java
@Component
public class ClubsRepresentationAssembler
        implements RepresentationModelAssembler<ClubsResponseDto, ClubsResponseDto> {

    @Override
    public ClubsResponseDto toModel(ClubsResponseDto dto) {
        // self 링크: 항상 포함
        dto.add(linkTo(methodOn(GuestClubController.class)
                .findClubById(dto.getId())).withSelfRel());

        // list 링크: 항상 포함
        dto.add(linkTo(methodOn(GuestClubController.class)
                .findAllClubs()).withRel("list"));

        // OPEN일 때: 로그인 여부에 따라 apply 또는 login 링크
        if (dto.getRecruitingStatus() == RecruitingStatus.OPEN) {
            if (isLoggedIn()) {
                dto.add(Link.of("/api/v1/member/clubs/" + dto.getId() + "/applications")
                        .withRel("apply"));
            } else {
                dto.add(Link.of("/api/v1/public/auth/login")
                        .withRel("login"));
            }
        }

        return dto;
    }

    private boolean isLoggedIn() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
            && auth.isAuthenticated()
            && !(auth instanceof AnonymousAuthenticationToken);
    }
}
```

---

## 4단계 — 컨트롤러 수정

### 현재 `GuestClubController`

```java
@GetMapping("/clubs")
public ResponseEntity<ApiResponseDto<List<ClubsResponseDto>>> findAllClubs() {
    List<ClubsResponseDto> clubs = guestClubService.findAllClubs();
    return ResponseEntity.ok(ApiResponseDto.success(clubs, "전체 클럽 목록 조회 성공 [메인페이지]"));
}

@GetMapping("/clubs/{clubId}")
public ResponseEntity<ApiResponseDto<ClubResponseDto>> findClubById(@PathVariable Long clubId) {
    ClubResponseDto club = guestClubService.findClubById(clubId);
    return ResponseEntity.ok(ApiResponseDto.success(club, "클럽 상세 정보 조회 성공"));
}
```

### 변경 후

```java
@RequiredArgsConstructor
public class GuestClubController {

    private final GuestClubService guestClubService;
    private final ClubsRepresentationAssembler clubsAssembler;

    @GetMapping("/clubs")
    public ResponseEntity<ApiResponseDto<CollectionModel<ClubsResponseDto>>> findAllClubs() {
        List<ClubsResponseDto> clubs = guestClubService.findAllClubs();

        // Assembler로 각 DTO에 링크 추가
        List<ClubsResponseDto> withLinks = clubs.stream()
                .map(clubsAssembler::toModel)
                .toList();

        // CollectionModel로 감싸서 컬렉션 자체에도 self 링크 추가
        CollectionModel<ClubsResponseDto> result = CollectionModel.of(
                withLinks,
                linkTo(methodOn(GuestClubController.class).findAllClubs()).withSelfRel()
        );

        return ResponseEntity.ok(ApiResponseDto.success(result, "전체 클럽 목록 조회 성공 [메인페이지]"));
    }

    @GetMapping("/clubs/{clubId}")
    public ResponseEntity<ApiResponseDto<ClubResponseDto>> findClubById(@PathVariable Long clubId) {
        ClubResponseDto club = guestClubService.findClubById(clubId);

        // self, list 링크: 항상 포함
        club.add(linkTo(methodOn(GuestClubController.class).findClubById(clubId)).withSelfRel());
        club.add(linkTo(methodOn(GuestClubController.class).findAllClubs()).withRel("list"));

        // OPEN일 때: 로그인 여부에 따라 apply 또는 login 링크
        if (club.getRecruitingStatus() == RecruitingStatus.OPEN) {
            if (isLoggedIn()) {
                club.add(Link.of("/api/v1/member/clubs/" + clubId + "/applications")
                        .withRel("apply"));
            } else {
                club.add(Link.of("/api/v1/public/auth/login")
                        .withRel("login"));
            }
        }

        return ResponseEntity.ok(ApiResponseDto.success(club, "클럽 상세 정보 조회 성공"));
    }

    private boolean isLoggedIn() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
            && auth.isAuthenticated()
            && !(auth instanceof AnonymousAuthenticationToken);
    }
}
```

---

## ApiResponseDto 호환성 주의

현재 응답 구조:
```json
{
  "success": true,
  "message": "...",
  "data": { ... }   // ← HATEOAS _links가 여기 들어감
}
```

`ApiResponseDto`로 `data` 필드 안에 `_links`가 들어가므로 구조는 그대로 유지된다.
클라이언트는 `response.data._links.apply` 또는 `response.data._links.login` 식으로 접근하면 된다.

별도로 설정할 것은 없지만, Jackson이 `_links`를 올바르게 직렬화하려면 `spring-boot-starter-hateoas`가
자동으로 등록하는 `HypermediaAutoConfiguration`이 활성화되어 있어야 한다 (기본값: 활성화됨).

---

## 변경 파일 요약

| 파일 | 변경 내용 |
|------|-----------|
| `build.gradle` | `spring-boot-starter-hateoas` 의존성 추가 |
| `ClubsResponseDto.java` | `extends RepresentationModel<ClubsResponseDto>` 추가 |
| `ClubResponseDto.java` | `extends RepresentationModel<ClubResponseDto>` 추가, `@AllArgsConstructor` 제거 검토 |
| `ClubsRepresentationAssembler.java` | 신규 생성 — 목록 DTO에 링크 추가 (인증 여부 분기 포함) |
| `GuestClubController.java` | Assembler 주입, 상세 링크 인라인 추가 |

---

## 검증 체크리스트

- [ ] `GET /api/v1/public/clubs` — `OPEN` + 로그인 O → `_links.apply` 포함
- [ ] `GET /api/v1/public/clubs` — `OPEN` + 로그인 X → `_links.login` 포함
- [ ] `GET /api/v1/public/clubs` — `CLOSED` → `apply`, `login` 둘 다 미포함
- [ ] `GET /api/v1/public/clubs/{id}` — 동일 조건으로 동일하게 적용
- [ ] `ApiResponseDto` 래퍼 구조 (`success`, `message`, `data`) 유지 여부
- [ ] 기존 테스트 깨지지 않는지 확인
s