# 포트폴리오 MVP 최소 API 설계

* 작성일: 2026-10-28
* 공개 API: Spring Boot. React → Spring Boot → 내부 FastAPI 경계를 유지한다.
* 2026-10-30 AUTH / USER 승인 정책은 FR에 반영한다. 아래에서 구현 대상과 후속 설계 경로를 구분한다.

## 이번 단계에서 구현

| 경로 | 응답과 의미 |
| --- | --- |
| GET /api/v1/health | Spring Boot 프로세스 응답. 200, {"status":"UP","service":"backend"}. DB·AI 준비 상태를 뜻하지 않음 |
| GET /api/v1/health/dependencies | 실제 PostgreSQL 연결과 내부 FastAPI /health 확인. 모두 UP이면 200, 하나라도 DOWN이면 503. status, database, aiService만 반환 |
| 내부 GET /health | FastAPI 프로세스 응답. 200, {"status":"UP","service":"ai-service"}. 외부 LLM 호출 성공을 뜻하지 않음 |

두 Spring GET 헬스 경로와 POST 회원가입·로그인만 공개한다. 아래 AUTH / USER 경로는 JWT로 보호하며 나머지 경로는 차단한다. 기본 계정·비밀번호를 생성하거나 기록하지 않는다. FastAPI에는 사용자 토큰 발급 기능이 없다. 개발용 Vite 프록시는 /api 요청을 Spring Boot로 전달한다. React에서 FastAPI를 직접 호출하지 않는다.

연결 점검은 제한된 타임아웃으로 수행하며 예외 상세, 접속 URL, 비밀번호를 응답 또는 로그에 기록하지 않는다. 헬스 상태에는 사용자 정보가 없다. 개발용 Compose의 HTTP 노출은 HTTPS 배포 완료가 아니다.

## 비즈니스 API 경로

AUTH / USER는 아래 상세 계약으로 구현한다. PLAN 이후 경로는 미구현 최소 설계이며 초안·삭제 등 보류 정책은 구현 전에 보완한다.

| 메서드·경로 | 책임 | 근거 |
| --- | --- | --- |
| POST /api/v1/auth/signup, /login, /logout | 계정 생성·인증 시작·종료. 아래 상세 계약 적용 | FR-AUTH-001~004 |
| GET /api/v1/users/me | 본인 프로필 조회. 닉네임 수정은 유예 | FR-USER-001 |
| GET /api/v1/users/me/workout-profile | 설정 여부와 기본 운동 정보 조회 | FR-USER-003 |
| PATCH /api/v1/users/me/workout-profile | 최초 설정 또는 부분 수정 | FR-USER-004 |
| POST /api/v1/plan-candidates | 조건과 최근 기록에 따른 규칙 기반 신규 후보 생성 | FR-PLAN-001~004 |
| PATCH /api/v1/plan-candidates/{id} | 현재 후보 편집만 수행 | FR-PLAN-006 |
| POST /api/v1/plan-candidates/{id}/confirm | 후보 현재 내용을 확정 계획으로 저장. 세션 시작 안 함 | FR-PLAN-005 |
| POST /api/v1/plan-candidates/{id}/reject | 내용 보존·사용 불가 처리. 신규 후보·세션 생성 안 함 | FR-PLAN-007 |
| POST /api/v1/sessions | 확정 계획 연결은 선택적. 소유권·진행 세션 중복 검사 | US-04-01~02, US-04-05 |
| GET /api/v1/sessions/current | 진행 중 세션 조회 | US-04-03 |
| POST /api/v1/sessions/{id}/end | 정상·빈 종료 확인 및 미완료 입력 보호. 상세 계약 보류 | US-04-04, US-04-06, US-06-07 |
| POST /api/v1/sessions/{id}/records | 일반 입력 또는 확인한 구조화 결과를 실제 기록으로 저장 | US-05-02~03, US-06-05 |
| PATCH, DELETE /api/v1/records/{id} | 본인 기록 수정·삭제. 상세 정책 보류 | US-05-04~05 |
| POST /api/v1/sessions/{id}/parse | Spring이 내부 AI 파싱 호출. 결과는 확인용 DTO이며 기록 저장 안 함 | US-06-01~04 |
| GET /api/v1/sessions?date=YYYY-MM-DD | 날짜별 본인 세션 목록. 시간대·노출 정책 보류 | US-07-01 |
| GET /api/v1/sessions/{id}, /api/v1/records/{id} | 본인 세션·기록 상세 조회 | US-07-02~03 |

후보 재생성 FR-PLAN-008과 웨이트·닉네임 수정은 이번 구현 대상이 아니다. 후속 비즈니스 API는 인증된 사용자에서 소유자를 도출하며 클라이언트 userId만 신뢰하지 않는다. FastAPI의 파싱 계약과 외부 AI 어댑터는 NLP 구현 때 정의한다.

## AUTH / USER 상세 계약 — 2026-10-30

인증은 `Authorization: Bearer <accessToken>`이며 쿠키·세션·Refresh Token을 사용하지 않는다. React 메모리에만 보관하고 새로고침 시 로그인 화면으로 돌아간다. JWT 응답은 `Cache-Control: no-store`로 제공한다.

| 메서드·경로 | 입력 | 성공 응답 |
| --- | --- | --- |
| POST /api/v1/auth/signup | email, password, nickname | 201 `{id,email,nickname}`. 토큰 발급 없이 로그인 화면으로 이동 |
| POST /api/v1/auth/login | email, password | 200 `{accessToken,tokenType:"Bearer",expiresIn:1800}` |
| POST /api/v1/auth/logout | Bearer Token, 본문 없음 | 무효화 커밋 후 204. 저장 실패는 503이며 성공으로 안내하지 않음 |
| GET /api/v1/users/me | Bearer Token | 200 `{id,email,nickname}`. 비밀번호·해시 미포함 |
| GET /api/v1/users/me/workout-profile/options | Bearer Token | 200 `{goals,experienceLevels,preferredTypes,equipment}`, 각각 `{value,label}[]` |
| GET /api/v1/users/me/workout-profile | Bearer Token | 200 `{configured,goal,experienceLevel,preferredTypes,equipment}`. 미설정은 false와 나머지 null |
| PATCH /api/v1/users/me/workout-profile | goal, experienceLevel, preferredTypes, equipment 중 변경 필드 | 200 최신 프로필. 최초 설정은 모두 필수, 수정은 하나 이상·누락 유지 |

* 목표 코드: `FITNESS` 체력 향상, `STRENGTH` 근력 향상, `RUNNING` 러닝 능력 향상, `HABIT` 운동 습관 형성.
* 경험 코드: `BEGINNER` 초급, `INTERMEDIATE` 중급, `ADVANCED` 상급.
* 유형 코드: `RUNNING` 러닝, `CALISTHENICS` 맨몸운동. 장비 코드: `NONE` 장비 없음, `MAT` 매트, `PULL_UP_BAR` 철봉, `DUMBBELL` 덤벨.
* Bean Validation의 이메일 형식을 사용하며 소문자로 정규화한다. 문자 수는 Unicode code point 기준이다. 비밀번호 원문 공백을 제거하거나 조합 제한을 추가하지 않는다.
* 회원가입·로그인에서 짝이 없는 UTF-16 surrogate는 400 VALIDATION_ERROR로 거부한다. 정상 surrogate pair·한글·이모지의 8~72자 정책과 기존 SHA-384 → Base64 → BCrypt 해시 형식은 유지하며, 해싱 단계에서도 손실성 인코딩을 허용하지 않는다.
* React의 이메일 입력은 text + inputMode=email을 사용하여 백엔드가 허용하는 Unicode 로컬 부분을 차단하지 않는다. 공백뿐인 입력과 254자 초과는 명시적으로 검사하며 형식은 서버가 최종 검증한다. 서버 검증 오류도 화면에 안내하고 ASCII 전용 제한을 추가하지 않는다.
* 프로필의 명시적 null·빈 배열·중복·미지원값·빈 변경은 400이다. NONE은 다른 장비와 동시 선택할 수 없다. 알 수 없는 JSON 필드도 400으로 거절하여 userId 등으로 소유자를 지정하지 못하게 한다.
* 공통 오류: `{code,message,fieldErrors:{필드명:설명}}`. 400 VALIDATION_ERROR, 401 UNAUTHENTICATED/INVALID_CREDENTIALS, 403 FORBIDDEN, 409 EMAIL_ALREADY_EXISTS, 503 SERVICE_UNAVAILABLE. 예상하지 못한 오류는 500 INTERNAL_ERROR이며 내부 예외·요청값은 노출하지 않는다.
* 로그인 실패는 동일한 401·메시지를 사용한다. 익명 보호 요청은 401, 인증 후 허용되지 않은 경로·다른 사용자 ID 경로는 403이다. `/me`는 JWT에서만 소유자를 구한다. 로그아웃한 JWT 재사용은 401이다.
* JWT 필터는 서명·시간·issuer·audience·사용자·jti 무효화를 검증한다. DB 장애 시 503으로 거절한다. 무효화 완료 응답 이후 들어오는 요청부터 차단하며 이미 처리 중인 요청을 소급 취소하지 않는다.

## 구현 차단 정책 공백

[도메인 모델의 공백 목록](portfolio-mvp-domain-model.md)을 먼저 검토한다. 특히 세션 종료 확인 계약, 기록 단위·삭제, 구조화 누락값·복수 저장·초안 보존, HISTORY 날짜 경계를 임의로 고정하지 않는다. JWT 운영 방식은 이번에 확정했으나 향후 운동 입력 초안의 인증 만료·재로그인 보존 흐름은 별도 미결정이다.
