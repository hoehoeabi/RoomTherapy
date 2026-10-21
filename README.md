# 🛋️ Room Therapy Backend (룸 테라피 서버)

Unity WebGL 방 꾸미기 게임과 웹페이지(굿즈 배송 신청 & 카카오 소셜 로그인)를 연동하는 스프링 부트 기반 백엔드 시스템입니다.

---

## 🛠️ 기술 스택 (Tech Stack)

- **언어 및 프레임워크**: Java 17, Spring Boot 4.1.1
- **보안 및 인증**: Spring Security, OAuth2 Client (Kakao), JJWT 0.12.5
- **인메모리 캐시 & 토큰 관리**: Redis (RTR / Refresh Token Rotation & 탈취 감지)
- **데이터베이스 & ORM**: Spring Data JPA, H2 (개발용) / MySQL (운영용)
- **화면 렌더링**: Thymeleaf (SSR)
- **빌드 도구**: Gradle

---

## 🚀 로컬 실행 방법 (Local Getting Started)

### 1. Redis 실행
Refresh Token 저장 및 RTR 관리를 위해 Redis가 필요합니다.
```bash
# Docker 사용 시
docker run -d -p 6379:6379 redis

# macOS Homebrew 사용 시
brew services start redis
```

### 2. 환경 설정 (`application-secret.yml`)
`src/main/resources/application-secret.yml` 파일을 생성하고 카카오 API 키와 JWT 비밀키를 입력합니다. (해당 파일은 `.gitignore`에 등록되어 안전하게 관리됩니다.)

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          kakao:
            client-id: YOUR_KAKAO_REST_API_KEY
            client-secret: YOUR_KAKAO_CLIENT_SECRET

jwt:
  secret: YOUR_BASE64_ENCODED_JWT_SECRET_KEY
```

> **배포 환경 (CI/CD)**: GitHub Repository Secrets에 `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET`, `JWT_SECRET`을 등록하여 환경 변수로 주입할 수 있습니다.

### 3. 서버 실행
```bash
./gradlew bootRun
```
실행 후 브라우저에서 `http://localhost:8080`에 접속합니다.

---

## 📐 인증 및 세션 아키텍처

- **Unity WebGL 연동 지원**: WebGL 게임 내 버튼 클릭 시 외부 웹 URL로 이동하며, 브라우저의 `HttpOnly, Secure Cookie`를 통해 별도의 복잡한 토큰 전달 없이 자연스럽게 로그인 상태를 유지합니다.
- **RTR (Refresh Token Rotation)**: Access Token(30분) 만료 시 Refresh Token(7일)으로 재발급받으며, 재발급마다 Refresh Token이 새롭게 교체됩니다. 이미 사용된 옛 토큰이 감지될 경우 즉시 토큰 탈취로 판단하여 Redis 세션을 무효화합니다.
- **하이브리드 인증 지원**: 브라우저 쿠키 인증뿐만 아니라 `Authorization: Bearer <token>` 헤더 방식도 지원하여, 향후 Unity 게임 내부에서 직접 REST API를 호출할 수 있도록 설계되었습니다.

---

## 🔗 주요 엔드포인트

| Method | Endpoint | 설명 | 접근 권한 |
| :--- | :--- | :--- | :--- |
| `GET` | `/` | 메인 홈 (로그인 상태에 따라 화면 전환) | 전체 공개 |
| `GET` | `/oauth2/authorization/kakao` | 카카오 OAuth2 간편 로그인 시작 | 전체 공개 |
| `GET` | `/delivery` | 클리어 인증코드 및 굿즈 배송 신청 페이지 | 인증 회원 전용 |
| `POST` | `/api/auth/reissue` | RTR 기반 JWT 토큰 재발급 | 전체 공개 (RefreshToken 필요) |
| `GET/POST`| `/logout` | 로그아웃 (Redis 토큰 파기 및 쿠키 무효화) | 전체 공개 |
