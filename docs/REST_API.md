# SMU-CLUB REST API 목록

> Base URL: `/api/v1`
> 인증: JWT Bearer Token (Authorization 헤더)

---

## 공개 API (인증 불필요)

### 동아리 조회

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/public/clubs` | 전체 동아리 목록 조회 (메인 페이지) |
| `GET` | `/public/clubs/{clubId}` | 동아리 상세 정보 조회 |

### 인증

| Method | Path | 설명 |
|--------|------|------|
| `POST` | `/public/auth/login` | 학번/비밀번호 로그인 |
| `POST` | `/public/auth/signup` | 회원가입 (학교 인증) |
| `POST` | `/public/auth/reissue` | Refresh Token으로 Access Token 재발급 |
| `POST` | `/auth/logout` | 로그아웃 |

---

## 회원 API (`ROLE_MEMBER` 이상)

### 지원서

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/member/clubs/{clubId}/apply` | 지원서 양식 조회 (회원 정보 + 질문 목록) |
| `POST` | `/member/clubs/{clubId}/apply` | 지원서 제출 |
| `POST` | `/member/clubs/application/upload-url` | 지원서 파일 업로드 Pre-Signed URL 생성 |

### 마이페이지

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/member/mypage/name` | 회원 이름 조회 |
| `GET` | `/member/mypage/applications` | 지원한 동아리 목록 |
| `GET` | `/member/mypage/applications/{clubId}/result` | 지원 결과 조회 |
| `GET` | `/member/mypage/applications/{clubId}/update` | 지원서 수정 폼 조회 |
| `PUT` | `/member/mypage/applications/{clubId}/update` | 지원서 수정 제출 |
| `GET` | `/member/mypage/update` | 개인정보 수정 폼 조회 |
| `PUT` | `/member/mypage/update/email` | 이메일 변경 |
| `PUT` | `/member/mypage/update/phone` | 전화번호 변경 |

---

## 동아리 운영진 API (`ROLE_OWNER` 이상)

### 동아리 관리

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/owner/club/managed-clubs` | 운영하는 동아리 목록 (마이페이지) |
| `GET` | `/owner/club/{clubId}` | 동아리 상세 정보 조회 (편집용) |
| `POST` | `/owner/club/upload-urls` | 동아리 이미지 업로드 Pre-Signed URL 생성 (복수) |
| `POST` | `/owner/club/register/club` | 동아리 등록 |
| `PUT` | `/owner/club/{clubId}` | 동아리 정보 수정 |

### 모집 관리

| Method | Path | 설명 |
|--------|------|------|
| `POST` | `/owner/club/{clubId}/start-recruitment` | 모집 시작 (CLOSED → OPEN) |
| `POST` | `/owner/club/{clubId}/close-recruitment` | 모집 마감 (OPEN → CLOSED) |

### 지원자 관리

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/owner/club/{clubId}/applicants` | 지원자 목록 조회 |
| `GET` | `/owner/club/{clubId}/applicants/{clubMemberId}` | 지원자 상세 정보 + 답변 |
| `PATCH` | `/owner/club/{clubId}/applicants/{clubMemberId}/status` | 지원자 상태 변경 (합격/불합격) |
| `GET` | `/owner/club/{clubId}/applicants/excel` | 합격자 명단 Excel 다운로드 |

### 이메일

| Method | Path | 설명 |
|--------|------|------|
| `POST` | `/owner/club/{clubId}/email` | 지원 결과 통보 이메일 발송 (비동기) |

### 질문 관리

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/owner/clubs/{clubId}/questions` | 지원서 질문 목록 조회 |
| `PUT` | `/owner/clubs/{clubId}/questions` | 지원서 질문 저장/수정 |

---

## 전체 API 요약

| 분류 | 개수 |
|------|------|
| 공개 API | 6개 |
| 회원 API | 11개 |
| 운영진 API | 13개 |
| **합계** | **30개** |

---

## 공통 응답 형식

```json
{
  "success": true,
  "code": null,
  "message": null,
  "data": { }
}
```

**오류 응답:**
```json
{
  "success": false,
  "code": "MEMBER_NOT_FOUND",
  "message": "해당 회원을 찾을 수 없습니다.",
  "data": null
}
```

---

## 주요 에러 코드

| HTTP 상태 | 코드 | 설명 |
|----------|------|------|
| 400 | `EMPTY_EMAIL_LIST` | 이메일 발송 대상 없음 |
| 400 | `NOT_ALLOWED_FILE_TYPE` | 허용되지 않은 파일 타입 |
| 401 | `LOGIN_FAILED` | 로그인 실패 |
| 401 | `UNIV_AUTH_FAILED` | 학교 인증 실패 |
| 401 | `INVALID_REFRESH_TOKEN` | 유효하지 않은 리프레시 토큰 |
| 404 | `MEMBER_NOT_FOUND` | 회원 없음 |
| 404 | `CLUB_NOT_FOUND` | 동아리 없음 |
| 409 | `MEMBER_ALREADY_EXISTS` | 이미 존재하는 회원 |
| 409 | `DUPLICATE_CLUB_NAME` | 중복된 동아리 이름 |
| 409 | `ILLEGAL_CLUB_STATE` | 동아리 상태 전환 불가 |
| 500 | `OCI_UPLOAD_FAILED` | 파일 업로드 실패 |

---

## 역할(Role) 계층

```
ADMIN > OWNER > MEMBER
```

| 역할 | 접근 가능 경로 |
|------|-------------|
| `MEMBER` | `/public/**`, `/auth/**`, `/member/**` |
| `OWNER` | 위 + `/owner/**` |
| `ADMIN` | 전체 |
