# 포트폴리오 MVP 최소 API 설계

* 작성일: 2026-10-28
* 공개 API: Spring Boot. React → Spring Boot → 내부 FastAPI 경계를 유지한다.
* 기존 FR은 수정하지 않는다. 아래에서 현재 구현과 후속 설계 경로를 구분한다.

## 이번 단계에서 구현

| 경로 | 응답과 의미 |
| --- | --- |
| GET /api/v1/health | Spring Boot 프로세스 응답. 200, {"status":"UP","service":"backend"}. DB·AI 준비 상태를 뜻하지 않음 |
| GET /api/v1/health/dependencies | 실제 PostgreSQL 연결과 내부 FastAPI /health 확인. 모두 UP이면 200, 하나라도 DOWN이면 503. status, database, aiService만 반환 |
| 내부 GET /health | FastAPI 프로세스 응답. 200, {"status":"UP","service":"ai-service"}. 외부 LLM 호출 성공을 뜻하지 않음 |

두 Spring GET 헬스 경로만 인증 없이 허용하고 나머지 요청은 Spring Security에서 차단한다. 회원가입·JWT 발급·사용자 데이터 API는 아직 없으며 기본 계정·비밀번호를 생성하거나 기록하지 않는다. FastAPI에는 사용자 토큰 발급 기능이 없다. 개발용 Vite 프록시는 /api 요청을 Spring Boot로 전달한다. React에서 FastAPI를 직접 호출하지 않는다.

연결 점검은 제한된 타임아웃으로 수행하며 예외 상세, 접속 URL, 비밀번호를 응답 또는 로그에 기록하지 않는다. 헬스 상태에는 사용자 정보가 없다. 개발용 Compose의 HTTP 노출은 HTTPS 배포 완료가 아니다.

## 다음 단계 경로 설계 — 미구현

아래 경로는 최소 기술 설계다. 토큰·입력·초안·삭제 정책이 보류된 부분은 구현 전에 상세 FR과 요청/응답 계약을 보완한다.

| 메서드·경로 | 책임 | 근거 |
| --- | --- | --- |
| POST /api/v1/auth/signup, /login, /logout | 계정 생성·인증 시작·종료. 토큰 계약 보류 | FR-AUTH-001~004 |
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

## 구현 차단 정책 공백

[도메인 모델의 공백 목록](portfolio-mvp-domain-model.md)을 먼저 검토한다. 특히 세션 종료 확인 계약, 기록 단위·삭제, 구조화 누락값·복수 저장·초안 보존, HISTORY 날짜 경계를 임의로 고정하지 않는다. 인증 만료·재로그인 보존 흐름과 JWT 운영 방식도 아직 결정하지 않았다.
