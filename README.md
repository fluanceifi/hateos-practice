

# SMU-CLUB :: 상명대학교 동아리 관리 및 홍보 플랫폼

<div align="center">
  <img width="500" height="500" alt="image" src="https://github.com/user-attachments/assets/ad111a4b-a9c0-4ca3-a9e5-31d7835a3aa0" />
</div>
<br/>

> SMU-CLUB은 상명대학교 학생들을 위한 동아리 관리 및 지원 플랫폼입니다.<br/>
> 동아리 운영진은 손쉽게 동아리를 홍보하고 지원자를 관리할 수 있으며, 학생들은 다양한 동아리 정보를 탐색하고 간편하게 지원할 수 있습니다.
<br/>

[![Java](https://img.shields.io/badge/java-17-%23ED8B00.svg?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2.5-%236DB33F.svg?style=for-the-badge&logo=spring&logoColor=white)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/react-%2320232a.svg?style=for-the-badge&logo=react&logoColor=%2361DAFB)](https://reactjs.org/)
[![MySQL](https://img.shields.io/badge/mysql-%2300f.svg?style=for-the-badge&logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Docker](https://img.shields.io/badge/docker-%230db7ed.svg?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)
[![GitHub Actions](https://img.shields.io/badge/github%20actions-%232671E5.svg?style=for-the-badge&logo=githubactions&logoColor=white)](https://github.com/features/actions)
<br/>

## 🏛️ 시스템 아키텍처
<img width="1162" height="1112" alt="image" src="https://github.com/user-attachments/assets/ac81ce2a-acad-450c-9d7c-eecb7dd556f8" />

## 📝 데이터베이스 관계도 (ERD)
<img width="802" height="716" alt="image" src="https://github.com/user-attachments/assets/90f50762-bea1-4b30-b995-9249f6ba99a5" />

<br/>

## ✨ 주요 기능

| 구분 | 기능 | 상세 내용 |
| --- | --- | --- |
| **인증 및 회원** | 회원 관리 | JWT 기반의 로그인/회원가입 및 인증/인가 |
| | 마이페이지 | 신청한 동아리 목록, 지원 결과 확인, 개인정보 수정 |
| | 학교 인증 | 대학교 웹메일을 통한 재학생 인증 |
| **동아리** | 동아리 탐색 | 카테고리별, 검색을 통한 동아리 정보 조회 |
| | 동아리 관리 | 동아리 정보(소개, 이미지 등) 생성 및 수정 |
| | 모집 관리 | 모집 기간 설정, 지원서 양식 커스터마이징 |
| **지원 및 관리** | 지원자 관리 | 지원자 정보 확인, 지원 상태(서류 합격 등) 변경 |
| | 지원서 제출 | 동아리별 커스텀된 지원서 작성 및 제출 |
| | 엑셀 다운로드 | 모든 지원자의 정보를 Excel 파일로 일괄 다운로드 |
| **알림** | Discord 알림 | 주요 이벤트(신규 지원, 계정 생성 등) 발생 시 Discord 웹훅 알림 |

<br/>

## ✍️ 개발 과정 및 트러블 슈팅
이 프로젝트를 진행하며 겪었던 문제들과 해결 과정을 기술 블로그에 기록하고 있습니다.

### 승준
- **[기술 포스팅]** [스케줄러가 조용히 실패했다: 계층 분리부터 트랜잭션 최적화, AOP 기반 에러 알림까지](https://fluanceifi.tistory.com/40)
- **[트러블 슈팅]** [100명한테 메일 보내는데 왜 서버가 100초동안 멈추는거야? (feat. 스레드)](https://fluanceifi.notion.site/Trouble-Shooting-100-100-feat-2b7f210247b28024b839c912e385c034?pvs=74)
- **[트러블 슈팅]** [JPA Fetch Join 사용 시 특정 엔티티가 조회되지 않는 현상 (Inner Join vs Left Join)](https://fluanceifi.notion.site/TroubleShooting-JPA-Fetch-Join-Inner-Join-vs-Left-Join-2d0f210247b2806a9c30ef003c5422d8)
- **[리팩토링]** [API 응답이 제각각인데... 막내 프론트가 버티고 있었다!](https://fluanceifi.notion.site/Refactoring-API-30df210247b280bab313d9f2252bcf9a?pvs=74)
<br/>

## 🛠️ 기술 스택

### Backend
- Java 17
- Spring Boot 3.2.5
- Spring Security + JWT
- Spring Data JPA (Hibernate)
- QueryDSL
- MySQL
- Swagger (API 문서 자동화)

### Frontend
- React (CRA)
- JavaScript / TypeScript
- Axios
- `frontend/package.json` 분석을 통해 구체화 예정

### Infrastructure & DevOps
- Docker & Docker Compose
- Nginx (리버스 프록시)
- OCI Object Storage (이미지 등 정적 파일 저장)
- GitHub Actions (CI/CD)

<br/>

## 🚀 시작하기

### 1. 프로젝트 클론
```bash
git clone https://github.com/your-repo/smu-club.git
cd smu-club
```

### 2. Docker Compose 실행
프로젝트에 필요한 DB 등 모든 서비스는 Docker Compose로 관리됩니다.
```bash
docker-compose --profile dev up -d --build
```
백엔드 서버는 `localhost:8080`에서 실행됩니다. <br>
API 문서는 `http://localhost:8080/swagger-ui/index.html` 에서 확인 가능합니다.<br>
<br>
프론트엔드 개발 서버는 `localhost:3000`에서 실행됩니다.

<br/>

## 🧪 부하 테스트 (예시)

> [!NOTE]
> 아직 공식적인 부하 테스트는 진행되지 않았습니다. 아래는 이후 작성할 테스트 예시 코드입니다.

메인 페이지의 동아리 목록을 조회하는 API에 대한 간단한 부하 테스트 스크립트입니다.

```javascript
// smoke-test.js
import http from 'k6/http';
import { sleep } from 'k6';

export const options = {
  vus: 10, // 10명의 가상 유저
  duration: '30s', // 30초 동안 실행
};

export default function () {
  // 모든 동아리 목록을 조회하는 API
  http.get('http://localhost:8080/api/clubs/all');
  sleep(1);
}
```

**테스트 실행:**
```bash
k6 run smoke-test.js
```

<br/>

## 🤝 기여 방법

SMU-CLUB은 여러분의 기여를 환영합니다.

### Commit Convention
본 프로젝트는 **Conventional Commits**를 따릅니다.
- `feat`: 새로운 기능 추가
- `fix`: 버그 수정
- `docs`: 문서 수정
- `style`: 코드 포맷팅, 세미콜론 누락 등 (코드 변경이 없는 경우)
- `refactor`: 코드 리팩토링
- `test`: 테스트 코드 추가/수정
- `chore`: 빌드 관련 파일 수정, 패키지 매니저 설정 변경 등

**커밋 메시지 형식:**
```
type(scope): subject
```

### Branch
- `main`: 배포 가능한 프로덕션 브랜치
- `develop`: 다음 릴리즈를 준비하는 개발 브랜치
- `feature/{issue-number}-{description}`: 기능 개발 브랜치

모든 작업은 `feature` 브랜치에서 시작하여 `develop` 브랜치로 Pull Request를 보내주세요.
