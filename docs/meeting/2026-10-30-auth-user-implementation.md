# 인증 및 운동 프로필 구현 회의

## 문서 정보

* 프로젝트: AI Love Workout
* 문서: Meeting
* 작성일: 2026-10-30

---

# 오늘 논의할 내용

* AUTH / USER 기능 요구사항 확인
* 회원가입 이후 인증 흐름
* JWT Access Token 발급 및 만료 정책
* Refresh Token 사용 여부
* 로그아웃 시 기존 JWT 무효화 정책
* 이메일·비밀번호·닉네임 입력 검증
* 운동 목표·경험 수준·주요 운동 유형·장비 허용값
* 사용자별 데이터 접근 제한
* Spring Boot / React 구현 및 검증 범위

---

# 주요 논의 사항

작업 시작 초안 이후 사용자가 AUTH + USER Vertical Slice 구현 정책을 승인했다. 회원가입 → 로그인 → 운동 프로필 최초 설정·조회·수정 → 로그아웃을 연결한다. 문서의 정책 승인과 실제 구현·검증 완료는 구분한다.

* 기존 `/me` 경로에서 인증된 사용자로 소유자를 결정하고 클라이언트의 사용자 식별자로 소유권을 바꾸지 않는다.
* 백엔드의 허용값 조회 계약을 React가 사용하여 운동 프로필 선택값의 불일치를 방지한다.
* DB 스키마는 Flyway 마이그레이션, Hibernate는 스키마 검증으로 관리한다. JWT 검증에는 Spring Security JOSE, 입력 검증에는 Bean Validation이 필요하다. 프론트엔드 UI 의존성은 추가하지 않는다.
* BCrypt의 72바이트 입력 한도로 승인된 72자 Unicode 비밀번호가 잘리지 않도록 도메인 구분 문자열과 UTF-8 비밀번호를 SHA-384 → Base64로 전처리한 뒤 BCrypt로 저장한다. 문자 수 기준은 Unicode code point이며 추가 조합 제한을 만들지 않는다. 이는 저장 방식의 기술 설계로, 원문·중간 해시는 저장·로깅하지 않는다.

초기 구현 후 Spring Boot 자동 테스트 47개(실제 PostgreSQL 통합 24개 포함)와 React 타입 검사·빌드가 통과했다. 별도 Compose에서 네 서비스 연결, 실제 저장·API 흐름과 백엔드 재시작 후 JWT 무효화 유지를 검증했다. 실제 DB 중지 중 로그아웃 503, 복구 후 기존 토큰 조회 200·로그아웃 재시도 204·재사용 401도 확인했다. 일반 브라우저 제어 런타임의 시간 초과는 Edge headless 검증으로 대체하여 전체 화면 흐름·부분 수정·새로고침 재로그인·로그아웃 및 모바일 화면을 확인했다. 초기 명령과 결과는 [개발 환경 문서](../development.md)에 기록되어 있다.

이후 사용자가 Chrome에서 가입·로그인·운동 프로필 최초 설정·저장·조회·수정 화면 진입·변경 없이 저장 시 안내·로그아웃을 직접 확인했다. 로그아웃 이전 JWT 재사용 시 보호 API 401과 새로고침 후 재로그인도 확인했다. 실제 값을 변경한 후 재조회하는 Chrome 수동 검증 여부는 미확인이며, 그 동작은 PostgreSQL 통합·headless 검증으로 확인했다.

* 보안 감사에서 잘못된 UTF-16 surrogate의 UTF-8 치환으로 비밀번호 입력이 같아지는 문제와, 백엔드가 허용한 Unicode 이메일을 React의 type=email이 차단하는 문제를 발견했다. 두 필수 문제를 최소 수정하고 기존 해시 호환성·입력 경계·가입·로그인 회귀 테스트를 추가했다.
* 최종 자동 테스트는 82개(실제 PostgreSQL 통합 38개, 단위·MVC 44개) 모두 통과했고 실패·오류·건너뜀은 0이다. React 타입 검사·빌드와 Docker 서비스 빌드가 성공했다. Edge headless에서 ASCII·Unicode 이메일 각각의 실제 폼 제출과 기존 전체 사용자 흐름도 통과했다.
* 보안 수정 후 첫 전체 회귀 실행은 기존 프로필 테스트 16개가 사전 회원가입 단계의 DB 503으로 실패했다. 환경 연결 상태를 확인한 뒤 추가 코드 변경 없이 같은 코드로 재실행해 전체 통과했다. 최초 실패 원인은 확정하지 않았다.
* UI/UX는 디자인 시스템의 기준을 먼저 정하고 기능 개발과 병행하며, 마지막에 전체 완성도를 개선하기로 논의했다. 방향은 Deep Green + Warm Ivory 기반의 운동 데이터 중심 UI다. 디자인 시스템 구축이나 전체 UI 개선 완료를 의미하지 않는다.

---

# 결정 사항

다음 기존 기준을 유지하며, 아래 AUTH / USER 정책은 이번 사용자 승인에 따라 반영한다.

* 사용자 인증·인가는 Spring Security가 담당하고 React → Spring Boot → 내부 FastAPI 구조를 유지한다.
* 1차 MVP는 러닝·맨몸운동 중심이며 운동 프로필 최초 설정·수정과 본인 프로필 조회를 포함한다. 닉네임 수정은 후속 구현으로 유예한다.
* SDD와 Project Rules에 따라 명세·설계·구현·테스트의 추적성을 유지한다.

* 가입 후 자동 로그인하지 않고 로그인 화면으로 이동한다.
* Access Token은 30분, Refresh Token은 사용하지 않는다. React 메모리에만 저장하고 새로고침 시 재로그인한다.
* JWT마다 jti를 발급한다. 로그아웃한 jti는 PostgreSQL에서 만료 시점까지 보관하고 서버 재시작 후에도 무효화를 유지한다. 모든 보호 요청에서 서명·만료·사용자 식별·무효화를 검증한다. 로그아웃 저장 실패는 성공으로 처리하지 않는다.
* 이메일은 형식 검증·소문자 정규화·최대 254자·중복 금지, 비밀번호는 8~72자·추가 조합 제한 없음·BCrypt 저장, 닉네임은 2~20자·중복 허용이다.
* 로그인 실패는 계정 존재 여부를 노출하지 않는다. 본인 데이터만 접근할 수 있으며 인증·권한 오류의 응답 구조를 통일한다. JWT 서명키는 환경 변수로 관리한다.
* Account Profile과 WorkoutProfile을 분리한다. 목표는 체력 향상·근력 향상·러닝 능력 향상·운동 습관 형성, 경험은 초급·중급·상급, 주요 운동 유형은 러닝·맨몸운동 중 하나 이상이다.
* 장비는 장비 없음·매트·철봉·덤벨이며 하나 이상 선택한다. 장비 없음은 다른 장비와 함께 선택할 수 없다. 유형·장비의 중복은 불가하다.
* 최초 설정에는 목표·경험·유형·장비가 모두 필요하다. 부분 수정에서 누락 항목은 유지하고 기존 후보·확정 계획은 변경하지 않는다. 허용값은 백엔드를 기준으로 한다.
* 가입·로그인에서 짝이 없는 UTF-16 surrogate는 400으로 거부하고 해싱 단계에서도 손실성 인코딩을 막는다. 정상 Unicode 8~72자와 기존 SHA-384 → Base64 → BCrypt 형식·해시 호환성은 유지한다.
* 이메일 형식은 백엔드 검증을 최종 기준으로 유지한다. React는 Unicode 이메일을 임의로 차단하지 않고 빈 입력·254자 제한을 명시적으로 검사하며 서버 오류를 안내한다. 새 ASCII 제한은 추가하지 않는다.
* 디자인 시스템을 먼저 정하고 기능 개발과 UI/UX 작업을 병행한 뒤 마지막에 전체 완성도를 개선한다. 디자인 방향은 Deep Green + Warm Ivory 기반의 운동 데이터 중심 UI로 한다.

---

# 보류 사항

작업 시작 시점에 보류했던 위 AUTH / USER 정책은 이번 승인으로 해소했다. 아래 항목은 이번 구현 밖에 남는다.

* 실제 HTTPS 배포 대상과 운영 시 서명키 교체·비밀정보 주입 절차
* HMAC pepper 등 비밀번호 저장의 운영 보안 검토와 향후 해시 형식 변경 시 버전·기존 계정 호환성 설계. 이번에 해싱 방식을 교체하지 않았다.
* 후속 닉네임 수정 기능과 기존 SESSION / RECORD / NLP / HISTORY 정책 공백
* PLAN의 세부 산정 규칙과 기존 ADR 최신화 범위. 이번 작업에서 변경하지 않는다.

---

# 영향받는 문서

* [functional-requirements.md](../requirements/functional-requirements.md): AUTH / USER 정책과 인수 조건의 기준
* [portfolio-mvp-scope-2026-11-01.md](../requirements/portfolio-mvp-scope-2026-11-01.md): 1차 포함 범위와 유예 기능의 기준
* [portfolio-mvp-domain-model.md](../architecture/portfolio-mvp-domain-model.md): User와 WorkoutProfile의 책임·관계 검토
* [portfolio-mvp-erd.md](../architecture/portfolio-mvp-erd.md): 계정·운동 프로필 저장 구조 검토
* [portfolio-mvp-api.md](../architecture/portfolio-mvp-api.md): 인증·프로필 API 계약 검토
* [관련 ADR](../architecture/adr/README.md) 및 Spring Boot / React 구현 코드: 후속 설계·구현 시 영향 확인
* [당일 Study](../study/2026-10-30-auth-user-implementation.md)와 [Journal](../journal/2026-10-30.md): 실제 구현·감사·최종 검증 및 회고 기록

AUTH / USER 구현 과정에서 Meeting·Functional Requirements·최소 도메인 모델·ERD·API에 승인 정책을 반영했다. 이번 작업 종료 기록 보완은 Study / Meeting / Journal 세 문서만 수정한다. 위 요구사항·설계·개발 환경 문서는 근거로 참조하며 기존 MVP·1차 범위·PLAN 요구사항과 ADR은 이번에 수정하지 않는다.

---

# 다음 작업

이번 AUTH + USER 구현·필수 결함 수정·최종 자동 테스트·Chrome 수동 및 headless 검증 이후 남은 작업이다.

* Chrome에서 실제 프로필 값을 변경한 뒤 재조회하여 유지되는지 수동 검증 보완
* Deep Green + Warm Ivory 방향의 디자인 시스템을 정리하고 다음 기능의 UI/UX 개발과 병행
* SESSION / RECORD의 미결정 정책과 상세 FR을 먼저 확정한 뒤 다음 Vertical Slice 구현
* 기존 승인 정책에 따라 규칙 기반 PLAN과 사용자 확인을 거치는 AI 구조화 연결을 후속 구현
* 실제 HTTPS 배포 대상 확정 및 배포 환경에서 핵심 흐름 검증
