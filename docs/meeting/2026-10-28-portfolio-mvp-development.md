# 포트폴리오 MVP 개발 전환 회의

## 문서 정보

* 프로젝트: AI Love Workout
* 문서: Meeting
* 작성일: 2026-10-28

---

# 오늘 논의할 내용

* 약 3개월 만의 프로젝트 재개와 현재 저장소·문서 상태 확인
* 2026-11-01 포트폴리오 MVP 목표와 5일 구현 일정
* 기존 승인 MVP와 1차 구현 범위의 구분
* SDD 방식 및 Project Rules를 유지하는 구현 전환
* 최소 도메인 설계와 개발 환경 구축의 다음 작업 범위

---

# 주요 논의 사항

* 작업 시작 시 Codex 저장소 분석에서 실행 가능한 서비스 코드가 없고 요구사항·ADR·학습 기록 중심으로 진행되어 있음을 확인하였다.
* 기존 MVP 전체 범위와 단기 포트폴리오 시연 범위를 분리하여 핵심 흐름부터 구현하기로 하였다.
* 러닝과 맨몸운동의 자연어 기록, 사용자 확인·수정, 세션에 저장, 날짜별 조회와 다음 계획 반영을 연결하는 흐름을 유지한다.
* 웨이트와 닉네임 수정은 기존 Must의 구현 유예이며 요구사항 삭제나 우선순위 변경으로 취급하지 않는다.
* PLAN 도메인의 기존 결정을 유지하고 SESSION·RECORD·NLP·HISTORY의 상세 FR 및 미결정 사항을 관련 비즈니스 구현 전에 검토해야 한다.
* 작업 시작 단계에서는 범위·계획 문서를 작성했고, 이후 최소 설계와 실행 골격 구축·검증을 완료하였다. 종료 시점에도 MVP 핵심 비즈니스 기능과 HTTPS 배포는 미완료이다.
* 최소 도메인 모델, ERD와 API 계약 문서를 작성하여 후보·확정 계획·세션·기록의 책임과 구현 범위를 구분했다. 미결정 정책은 별도로 남겼다.
* React / Spring Boot / FastAPI / PostgreSQL 구조를 유지하고 Docker Compose 기반 로컬 통합 환경을 구성했다.
* Spring Boot Health·Dependencies Health, FastAPI Health, PostgreSQL 실제 쿼리와 Vite 프록시 경유 연결을 검증했다. AI 서비스 장애 시 503 응답과 복구도 확인했다.
* Spring Boot 자동 테스트 8개, FastAPI 자동 테스트 3개와 React 타입 검사·빌드가 통과했다. 검증 결과와 미검증 범위는 docs/development.md에 기록했다.
* 향후 코드의 주석 작성 기준을 정리하고 기존 코드의 책임·접근 제어·통신·예외 처리 등에 필요한 주석을 보완했다.
* 작업 시작 시 합의한 일정은 10/28 범위·설계·환경, 10/29 인증·프로필·세션·일반 기록, 10/30 계획, 10/31 AI·통합, 11/01 테스트·HTTPS 배포·README·시연 준비였다. 이 일정은 실제 완료 결과와 구분한다.

---

# 결정 사항

* 포트폴리오 MVP 마감 목표는 2026-11-01로 한다.
* 러닝과 맨몸운동을 1차 구현 대상으로 확정한다.
* 웨이트, 닉네임 수정과 일부 부가기능은 후속 구현으로 유예한다.
* 후보 재생성(FR-PLAN-008), 고급 통계, 웨어러블 연동, Redis 기반 최적화와 기타 Should/Could 기능은 1차 구현에 포함하지 않는다. 기존 우선순위와 MVP 제외 정책은 유지한다.
* 기존 APPROVED 요구사항은 보존하고 삭제하거나 수정하지 않는다.
* 별도 1차 구현 범위 문서인 portfolio-mvp-scope-2026-11-01.md를 생성한다. 해당 문서의 승인은 구현 범위 승인으로 한정한다.
* React, Spring Boot, FastAPI, PostgreSQL 구조를 유지한다. 인증은 Spring Security가 담당하고 FastAPI는 내부 AI 서비스로 둔다.
* 규칙 기반 운동 계획과 AI 자연어 기록 구조화를 유지한다. AI가 전체 운동 계획을 임의로 결정하거나 사용자 확인 없이 최종 기록을 저장하지 않는다.
* SDD의 핵심 추적성 및 리뷰 원칙과 Project Rules를 유지한다.
* 1차 구현 완료와 원래 MVP 전체 완료를 구분하며 기존 FR을 이번 문서 작성만으로 구현 완료 처리하지 않는다.
* 기존 승인 정책에 근거한 최소 Domain Model·ERD·API 계약을 구현 출발점으로 작성하고, 비즈니스 API와 도메인 테이블의 구현은 후속 작업으로 구분한다.
* 로컬 통합 환경은 Docker Compose로 구성한다. JDK 21로 빌드·실행하되 Java 컴파일 대상은 17로 유지한다.
* 프로세스 Health와 Dependencies Health를 분리하여 검증하고, Mock 자동 테스트와 실제 서비스 연결 검증을 구분한다.
* 향후 작성·수정하는 코드의 주석 규칙을 AGENTS.md에 추가하기로 결정하고 영문 규칙 16개를 반영했다. 기존 코드에는 필요한 한국어 주석·Javadoc·Docstring을 보완하며 동작은 변경하지 않는다.

---

# 보류 사항

로컬 개발 환경 구축과 기본 서비스 연결 검증은 완료했으므로 보류 대상에서 제외한다. 아래 정책과 운영 배포 관련 결정은 여전히 남아 있다.

* 인증 토큰 종류·유효기간·갱신·보관·로그아웃 무효화 등 운영 세부 정책
* 이메일·비밀번호·닉네임의 입력 검증 정책
* 지원 운동 종목, 운동 프로필 허용값과 운동 계획 산정 규칙
* 운동 가능 시간 범위, 최근 운동 기록 기간과 휴식 시간 산정 관계
* 운동 기록 저장 단위, 여러 입력 병합 및 미완료 초안 처리 방식
* 자연어 원문·초안 보존, 인증 만료 시 입력 보호와 기록 삭제·빈 세션 조회 정책
* 후보 수정 저장 시점, 수정 이력 및 추천 이유의 확정 계획 보존 여부
* 실제 HTTPS 배포 대상 및 운영 배포 구성 확정
* 기존 ADR 최신화 필요 범위의 후속 검토

---

# 영향받는 문서

* [portfolio-mvp-scope-2026-11-01.md](../requirements/portfolio-mvp-scope-2026-11-01.md): 작성 완료한 1차 구현 범위 기준 문서
* [portfolio-mvp-domain-model.md](../architecture/portfolio-mvp-domain-model.md): 최소 도메인 책임·관계와 정책 공백 정리
* [portfolio-mvp-erd.md](../architecture/portfolio-mvp-erd.md): 최소 데이터 관계 설계
* [portfolio-mvp-api.md](../architecture/portfolio-mvp-api.md): 현재 헬스 API와 후속 비즈니스 API 계약 구분
* [development.md](../development.md): 개발 환경 실행 방법과 검증 결과 기록
* [AGENTS.md](../../AGENTS.md): Code Documentation and Comment Rules 16개 추가
* [functional-requirements.md](../requirements/functional-requirements.md): 후속 상세 명세 및 구현·테스트 추적성 검토 대상
* [user-stories.md](../requirements/user-stories.md): 1차 포함 범위와 유예 범위의 근거
* [mvp-scope.md](../requirements/mvp-scope.md): 기존 전체 MVP 기준이며 APPROVED 상태와 내용을 유지
* [관련 ADR](../architecture/adr/README.md): ADR-001~004의 최신 요구사항 반영 필요 범위 검토 대상

기존 승인 요구사항과 기존 FR·ADR은 수정하지 않았다. 위 목록은 새로 작성한 문서, 주석 규칙을 추가한 AGENTS.md와 기존 근거·후속 검토 문서를 구분한 것이다.

---

# 다음 작업

* 1차 필수 사용자 스토리에 대응하는 누락 FR과 미결정 정책 검토
* AUTH + USER의 인증 토큰·입력 검증·운동 프로필 세부 정책 확정
* 회원가입·로그인·운동 프로필 기능 구현
* SESSION / RECORD와 일반 입력 기록 저장·조회 흐름 구현
* 운동 계획 세부 규칙을 확정한 뒤 후보 생성·수정·확정·거절 구현
* 사용자 확인·수정 후 저장하는 AI 구조화 기능 연결
* 핵심 비즈니스 기능의 테스트 추가, HTTPS 배포와 시연 준비
