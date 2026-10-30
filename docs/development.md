# 포트폴리오 MVP 개발 환경

## 현재 구현 범위

React 회원가입·로그인·운동 프로필 최초 설정·조회·부분 수정·로그아웃과 Spring Boot·PostgreSQL AUTH / USER API를 제공한다. Spring Boot·FastAPI 헬스 및 연결 점검도 유지한다. 운동 기록·추천·NLP 비즈니스 기능은 아직 없다. 헬스 응답 성공은 비즈니스 기능 또는 외부 LLM 성공을 뜻하지 않는다.

## 도구와 선택 이유

* JDK 21: 현재 설치된 LTS JDK를 사용하여 추가 JDK 설치를 피한다. Gradle의 --release 17로 기존 Java 17 언어·바이트코드·표준 API 기준을 유지한다. 실제 실행 검증은 JDK 21이며 JDK 17에서의 실행은 별도 검증 대상이다.
* Spring Boot 3.5.16, Gradle 8.14.3: Java 17 기준과 설치된 JDK 21을 지원하는 조합이다. [Spring Boot 호환성](https://docs.spring.io/spring-boot/3.5/system-requirements.html), [Gradle JVM 호환성](https://docs.gradle.org/current/userguide/compatibility.html)을 근거로 선택했다. Spring Security·JPA·JDBC·테스트 버전은 Boot BOM으로 맞춘다.
* React 19.3.1, Vite 8.3.5, TypeScript 7.0.2: 기본 UI와 타입 검사·개발 프록시·번들에 사용한다. @vitejs/plugin-react 6.1.2와 React 타입 19.3.0을 사용한다. Node 22.12 이상을 요구하며 현재 Node 24.19.0으로 검증한다. 정확한 의존성은 package-lock.json으로 고정한다.
* Python 3.12, FastAPI 0.162.0, Uvicorn 0.54.0: 내부 HTTP 서비스의 실행에 필요하다. pytest 9.2.0·httpx 0.28.1은 테스트용이며 컨테이너 실행 이미지에는 설치하지 않는다.
* PostgreSQL 17: 로컬 DB를 Compose로 실행한다. 이미지의 17 태그는 해당 메이저의 패치 이미지를 받는다. 실제 실행 버전은 SELECT version()으로 확인한다.

AUTH / USER 추가 의존성은 Bean Validation(요청 검증), Spring Security JOSE(JWT 서명·검증), Flyway core + PostgreSQL 모듈(스키마 버전 관리)이며 Boot BOM을 따른다. [Spring Security JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html), [Flyway PostgreSQL 모듈](https://documentation.red-gate.com/fd/postgresql-database-277579325.html)을 참고한다. OpenAI SDK, Redis, H2, 프론트엔드 상태 관리 라이브러리, Nginx와 AWS 도구는 추가하지 않는다.

## 전체 Compose 실행

저장소 루트의 PowerShell에서 실행한다. Docker Desktop의 Linux 컨테이너 엔진이 준비되어 있어야 한다.

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
# .env의 POSTGRES_PASSWORD와 JWT_SECRET을 로컬에서 직접 설정한다. 기존 .env는 덮어쓰지 않는다.
# JWT_SECRET은 Base64 인코딩된 최소 32바이트 난수여야 한다. 실제 키를 공유·커밋하지 않는다.
docker compose --env-file .env -f infra/compose.yaml config --quiet
docker compose --env-file .env -f infra/compose.yaml up -d --build
docker compose --env-file .env -f infra/compose.yaml ps
./scripts/check-health.ps1
```

환경 예제에는 실제 비밀번호·키가 없다. .env는 Git 제외 대상이며 필수 비밀번호·JWT 서명키가 없으면 Compose가 실패한다. 기존 환경도 JWT_SECRET을 추가해야 한다. 설정 확인에는 비밀값이 출력되지 않는 config --quiet를 사용한다. PostgreSQL 볼륨의 기존 계정은 환경 변수 변경만으로 갱신되지 않으므로 기존 볼륨에 다른 비밀번호를 적용하거나 데이터를 삭제하지 않는다.

| 접속 | 용도 |
| --- | --- |
| http://127.0.0.1:5173 | React 개발 화면 및 /api 프록시 |
| http://127.0.0.1:8080/api/v1/health | Spring Boot 프로세스 응답 |
| http://127.0.0.1:8080/api/v1/health/dependencies | PostgreSQL + FastAPI 실제 연결 확인 |
| http://127.0.0.1:8000/health | FastAPI 프로세스 응답 |

Compose는 개발용 Vite 서버를 사용하고 호스트 포트는 127.0.0.1에만 바인딩한다. FastAPI 포트 공개는 로컬 진단용이며 React 통신 경계는 Spring Boot를 유지한다. 운영용 정적 서빙과 HTTPS 배포는 이번 단계 범위 밖이다.

서비스를 잠시 멈출 때는 docker compose --env-file .env -f infra/compose.yaml stop을 사용한다. 데이터 볼륨 삭제 명령은 이 안내에 포함하지 않는다.

## 호스트에서 개발

루트 PowerShell에서 DB 비밀번호를 프로세스 환경 변수로 설정한다. 실제 비밀번호를 명령문에 하드코딩하거나 출력하지 않는다. Spring Boot는 루트 .env를 자동으로 읽지 않는다.

```powershell
$env:POSTGRES_PASSWORD = Read-Host '로컬 PostgreSQL 비밀번호' -MaskInput
$env:JWT_SECRET = Read-Host 'Base64 인코딩한 로컬 JWT 서명키' -MaskInput
docker compose -f infra/compose.yaml up -d postgres
```

각 서비스는 별도 터미널에서 실행한다. 비밀번호와 DB_HOST·DB_PORT·POSTGRES_DB·POSTGRES_USER는 백엔드 터미널에도 환경 변수로 전달해야 한다. 기본 호스트는 127.0.0.1:5432이며 비밀번호 외 기본값은 .env.example에 있다.

```powershell
# AI 서비스 터미널: 저장소 루트에서
python -m venv ai-service/.venv
ai-service/.venv/Scripts/python.exe -m pip install -r ai-service/requirements-dev.txt
Set-Location ai-service
.venv/Scripts/python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

```powershell
# 백엔드 터미널: 저장소 루트에서. POSTGRES_PASSWORD 환경 변수가 필요하다.
$env:GRADLE_USER_HOME = Join-Path (Get-Location) 'backend/.gradle/user-home'
Set-Location backend
./gradlew.bat bootRun --no-daemon
```

```powershell
# 프론트엔드 터미널: 저장소 루트에서
Set-Location frontend
npm.cmd ci
npm.cmd run dev
```

로컬 FastAPI 기본 주소는 http://127.0.0.1:8000, Vite의 Spring 기본 주소는 http://127.0.0.1:8080이다. AI_SERVICE_URL과 BACKEND_URL 환경 변수로 바꿀 수 있다. BACKEND_URL은 Vite 서버용이며 DB 비밀값을 VITE_ 변수로 넘기지 않는다.

## 빌드와 최소 테스트

```powershell
# 저장소 루트에서
$env:GRADLE_USER_HOME = Join-Path (Get-Location) 'backend/.gradle/user-home'
./backend/gradlew.bat -p backend test bootJar --no-daemon
ai-service/.venv/Scripts/python.exe -m pytest -c ai-service/pyproject.toml ai-service/tests
npm.cmd --prefix frontend run build
./scripts/check-health.ps1
```

기본 백엔드 테스트는 헬스·JWT 시간과 서명·Unicode 비밀번호·프로필 규칙·저장소 장애의 HTTP 계약을 검증한다. 실제 PostgreSQL 테스트는 아래 별도 설정이 있을 때 실행하며, 미설정 시 AuthUserPostgresTest는 건너뛴다. Python 테스트는 TestClient 기반이며 실제 서비스 연결은 check-health.ps1로 구분한다. 프론트엔드 build는 타입 검사와 번들 생성이며 브라우저 상호작용 테스트가 아니다.

## AUTH / USER 통합 검증

```powershell
# Docker Desktop과 JDK·Node가 준비된 루트 PowerShell 7.2 이상에서 실행한다.
./scripts/verify-auth.ps1
```

* 매번 별도 Compose 프로젝트와 PostgreSQL 볼륨을 만들며 기존 데이터는 삭제하지 않는다. 포트는 DB 15432, Spring 18080, React 15173, FastAPI 18000을 사용하므로 해당 포트가 비어 있어야 한다.
* 임시 DB 비밀번호·JWT 키는 난수로 생성하여 프로세스 환경·테스트 컨테이너에만 전달한다. 실제 값을 파일·콘솔에 출력하지 않는다.
* 별도 `auth_integration` DB를 생성하고 AUTH_TEST_DB_URL·AUTH_TEST_DB_USER·AUTH_TEST_DB_PASSWORD를 테스트 프로세스에 전달한다. 테스트는 H2가 아니라 PostgreSQL 마이그레이션·제약조건·실제 HTTP·Spring 서버 재시작을 확인한다.
* Compose 백엔드에서도 가입 → 로그인 → 최초 설정 → 조회·부분 수정 → 로그아웃 → 기존 JWT 401을 확인하고, 컨테이너 재시작 후 401 유지와 실제 SQL 조회까지 수행한다. 브라우저 조작 검증은 별도다.
* 검증 프로젝트의 PostgreSQL을 잠시 중지하여 로그아웃 503을 확인하고 finally에서 DB를 다시 시작한다. 복구 후 같은 토큰으로 조회할 수 있는지와 재시도 로그아웃 204를 확인한다.
* 성공·실패 후에도 데이터와 컨테이너를 삭제하지 않는다. 출력한 프로젝트명으로 해당 컨테이너만 중지할 수 있다. 새 실행은 새 프로젝트를 사용한다.
* 실행 환경에서 JWT_SECRET은 기존과 같은 값을 유지해야 재시작 후 미로그아웃 토큰의 유효성도 유지된다. 서버에는 Refresh Token이 없고 브라우저 새로고침은 재로그인 흐름이다.
* Flyway가 AUTH / USER 세 테이블만 추가하고 JPA는 validate만 수행한다. 이미 비어 있지 않으면서 Flyway 이력이 없는 DB는 자동 baseline하지 않고 실패하도록 두므로 적용 전 DB 상태를 확인한다.

승인 정책과 요청·응답·오류 계약은 [FR 7.1절](requirements/functional-requirements.md), [API](architecture/portfolio-mvp-api.md), [도메인 모델](architecture/portfolio-mvp-domain-model.md), [ERD](architecture/portfolio-mvp-erd.md)를 따른다. BCrypt 입력 전처리와 문자 수 기준도 도메인 모델에서 설명한다.

Gradle Wrapper는 고정 배포판과 SHA-256을 사용한다. 최초 실행과 npm·pip 설치, Docker 이미지 빌드에는 네트워크 접근이 필요하다. DB가 없거나 비밀번호가 잘못되면 정상 애플리케이션 준비로 안내하지 않는다.

## 2026-10-30 AUTH / USER 실행 검증 결과

| 항목 | 결과 |
| --- | --- |
| Spring Boot | JDK 21·Java 17 컴파일 대상, test·bootJar 성공 |
| 자동 테스트 | 47개 통과, 실패·오류·건너뜀 0. PostgreSQL 실제 HTTP 통합 24개 + 단위·MVC 23개 |
| React | npm run typecheck·build 성공 |
| Compose | ai-love-workout-auth-20261030160209 프로젝트 네 서비스 빌드·실행 성공. 기존 프로젝트·볼륨 삭제 없음 |
| PostgreSQL | 17.11에서 Flyway V1, 사용자·프로필·jti 실제 저장·SQL 조회·배열 제약조건 확인 |
| API | 가입 → 별도 로그인 → 미설정 조회 → 최초 설정 → 조회·부분 수정 → 로그아웃 성공 |
| 재시작 | 테스트의 Spring 서버 재기동 후 무효화 JWT 401·다른 유효 JWT 200. Compose 백엔드 컨테이너 재시작 후에도 무효화 JWT 401 |
| 실제 DB 장애 | PostgreSQL 중지 중 로그아웃 503 SERVICE_UNAVAILABLE, DB 복구 후 동일 토큰 조회 200·로그아웃 재시도 204·재사용 401. DB 트랜잭션 시작 실패의 필터 회귀 테스트도 통과 |
| 서비스 통신 | Vite /api 프록시 → Spring 의존성 헬스의 database·aiService UP, FastAPI /health UP |
| 브라우저 | 설치된 Edge headless에서 실제 DOM으로 전체 사용자 흐름·수정 필드만 PATCH·새로고침 재로그인·재조회·로그아웃 확인 |
| 저장소·화면 | 새 브라우저 프로필에서 localStorage·sessionStorage·쿠키 미사용 확인. 1280px 데스크톱·390px 모바일 캡처 확인 및 가로 넘침 없음 |
| 로그 점검 | 백엔드 실행 로그·JUnit 보고서에서 JWT 형태 원문 발견 0, 기본 생성 비밀번호 로그 없음. 실제 .env 파일 생성 없음 |

* AUTH 테스트 근거: `AuthPolicyTest`, `SecurityFailureTest`, `AuthUserPostgresTest`. USER 근거: `WorkoutProfileServiceTest`와 `AuthUserPostgresTest`의 최초 설정·부분 수정·16가지 잘못된 PATCH·소유권 검증이다.
* 초기 브라우저 제어 런타임은 연결 시간 초과로 사용할 수 없었다. 설치된 Edge headless + Node 기본 모듈로 대체했다. 첫 검증 스크립트의 DOM JavaScript 변수 재선언 오류를 수정하고, 저장 완료 화면 대기 조건을 보완한 최종 실행은 통과했다.
* UI 재검증: `node scripts/check-auth-ui.mjs`. 기본 URL은 http://127.0.0.1:15173, 다른 설치 경로는 BROWSER_PATH, 다른 로컬 검증 주소는 AUTH_UI_URL 환경 변수로 지정한다. 실제 사용자 계정이 아닌 실행 중 생성한 테스트 데이터만 사용한다.
* 캡처는 Git 제외 경로 `tmp/auth-user-ui/`의 login-desktop.png, profile-desktop.png, profile-mobile.png, login-mobile.png다. 브라우저는 임시 프로필을 사용하고 검증 후 해당 headless 프로세스만 종료했다.
* 일반 브라우저의 수동·다중 브라우저 검증과 HTTPS 배포는 수행하지 않았다. SESSION / RECORD / PLAN / NLP 기능과 닉네임 수정은 이번에 구현하지 않았다. Study·Journal은 작업 시작 문서 상태를 유지했다.
* 검증 서비스는 http://127.0.0.1:15173 에 남겨두었다. 검증용 프로젝트만 중지하려면 `docker stop ai-love-workout-auth-20261030160209-frontend-1 ai-love-workout-auth-20261030160209-backend-1 ai-love-workout-auth-20261030160209-ai-service-1 ai-love-workout-auth-20261030160209-postgres-1`을 사용할 수 있다. 볼륨 삭제 명령은 실행하지 않았다.

### 실제 실행한 주요 명령

* 읽기 전용 확인: Get-Content, rg, Test-Path, Get-FileHash, git status·show·diff·ls-files, java·node·Docker 버전 확인.
* `./backend/gradlew.bat -p backend test bootJar --no-daemon`: 초기 단위·MVC 빌드 검증.
* `./scripts/verify-auth.ps1`: Compose config --quiet·up -d --build·ps, 테스트 전용 createdb, AUTH_TEST_DB 환경에서 Gradle test·bootJar --rerun-tasks, npm typecheck·build, 실제 API, backend restart, PostgreSQL SELECT.
* `node scripts/check-auth-ui.mjs`, `node --check scripts/check-auth-ui.mjs`, PowerShell Parser: UI 흐름과 검증 스크립트 문법 확인.
* docker logs·exec 및 Invoke-WebRequest: 토큰 원문을 출력하지 않는 로그 점검, PostgreSQL version(), Vite 프록시·FastAPI 헬스 확인.
* 최종 실패 처리 보완 후 PostgreSQL 자동 테스트·bootJar 재실행, 검증 백엔드만 Compose up -d --build --no-deps backend로 갱신, 검증용 postgres만 docker stop/start하여 실제 장애·복구 API를 별도 확인했다.
* `git diff --check`, SHA256 비교: 공백과 변경 범위·기존 파일 보존 확인. PLAN 이하 FR은 원본과 동일하다. Commit·Push는 실행하지 않았다.

### 이번 작업 생성·수정 파일

작업 시작 시 이미 미추적이던 10월 30일 Study·Meeting·Journal 중 Meeting만 수정했다. 아래는 해당 시작 시점 대비 신규 28개·수정 18개이며 기존 파일 삭제는 없다.

신규 파일:

* `backend/src/main/java/com/ailoveworkout/auth/AuthController.java`
* `backend/src/main/java/com/ailoveworkout/auth/AuthService.java`
* `backend/src/main/java/com/ailoveworkout/auth/CurrentUser.java`
* `backend/src/main/java/com/ailoveworkout/auth/JwtAuthenticationFilter.java`
* `backend/src/main/java/com/ailoveworkout/auth/JwtService.java`
* `backend/src/main/java/com/ailoveworkout/auth/PasswordHashing.java`
* `backend/src/main/java/com/ailoveworkout/auth/RevokedToken.java`
* `backend/src/main/java/com/ailoveworkout/auth/RevokedTokenRepository.java`
* `backend/src/main/java/com/ailoveworkout/common/ApiErrors.java`
* `backend/src/main/java/com/ailoveworkout/common/ApiException.java`
* `backend/src/main/java/com/ailoveworkout/common/CharacterLength.java`
* `backend/src/main/java/com/ailoveworkout/config/JwtConfig.java`
* `backend/src/main/java/com/ailoveworkout/user/User.java`
* `backend/src/main/java/com/ailoveworkout/user/UserController.java`
* `backend/src/main/java/com/ailoveworkout/user/UserRepository.java`
* `backend/src/main/java/com/ailoveworkout/user/WorkoutOptions.java`
* `backend/src/main/java/com/ailoveworkout/user/WorkoutProfile.java`
* `backend/src/main/java/com/ailoveworkout/user/WorkoutProfileRepository.java`
* `backend/src/main/java/com/ailoveworkout/user/WorkoutProfileService.java`
* `backend/src/main/resources/db/migration/V1__auth_user.sql`
* `backend/src/test/java/com/ailoveworkout/auth/AuthUserPostgresTest.java`
* `backend/src/test/java/com/ailoveworkout/auth/SecurityFailureTest.java`
* `backend/src/test/java/com/ailoveworkout/config/AuthPolicyTest.java`
* `backend/src/test/java/com/ailoveworkout/user/WorkoutProfileServiceTest.java`
* `frontend/src/App.tsx`
* `frontend/src/api.ts`
* `scripts/check-auth-ui.mjs`
* `scripts/verify-auth.ps1`

수정 파일:

* `docs/meeting/2026-10-30-auth-user-implementation.md`
* `.env.example`
* `README.md`
* `backend/README.md`
* `backend/build.gradle`
* `backend/src/main/java/com/ailoveworkout/config/SecurityConfig.java`
* `backend/src/main/resources/application.yml`
* `backend/src/test/java/com/ailoveworkout/health/HealthControllerTest.java`
* `docs/architecture/portfolio-mvp-api.md`
* `docs/architecture/portfolio-mvp-domain-model.md`
* `docs/architecture/portfolio-mvp-erd.md`
* `docs/development.md`
* `docs/requirements/functional-requirements.md`
* `frontend/README.md`
* `frontend/src/main.tsx`
* `frontend/src/style.css`
* `infra/compose.yaml`
* `scripts/README.md`

## 2026-10-28 실행 검증 결과

| 항목 | 확인 결과 |
| --- | --- |
| 호스트 Spring Boot | Gradle test·bootJar 성공. 자동 테스트 8개 통과 |
| Java 호환성 | JDK 21.0.12.1에서 빌드. javap major version 61(Java 17) 확인. JDK 17 실행은 미검증 |
| 호스트 React | TypeScript 검사·Vite 빌드 성공. Linux 컨테이너에서도 npm run build 성공 |
| 호스트 FastAPI | Python 3.12.10, pytest 3개 통과 |
| Compose | 별도 ai-love-workout-verify-20261028 프로젝트에서 4개 서비스 이미지 빌드·기동 성공 |
| 실제 PostgreSQL | 17.11에서 SELECT version(), 1 성공. Spring JDBC 연결 점검 UP |
| 실제 HTTP | Spring·FastAPI 헬스, React HTML, Vite → Spring → FastAPI/DB 연결 점검 통과 |
| 접근 차단·장애 | 미구현 보호 경로 403. AI 중지 시 의존성 API 503/aiService DOWN, 재시작 후 UP 복구 확인 |
| 보안 로그 | 실행 백엔드 및 최종 테스트 결과에서 기본 생성 비밀번호 로그 없음 |
| 검증 후 상태 | 검증용 컨테이너 4개만 중지. 별도 데이터 볼륨·컨테이너는 보존, 기존 데이터 삭제 없음 |

선택된 주요 BOM 의존성은 Spring Security 6.5.11, Spring Data JPA 3.5.13, Hibernate 6.6.53.Final, PostgreSQL JDBC 42.7.11이다.

초기 React 빌드의 CSS 타입 선언 오류는 vite-env.d.ts를 추가해 해결했다. MVC 슬라이스 테스트에서 기본 계정 자동 구성도 명시적으로 제외했다. Docker 엔진 미실행은 설치된 Docker Desktop 시작으로 해결했다. 샌드박스의 외부 저장소 TLS·Docker 설정 접근 제한은 승인된 실행으로 검증했다.

FastAPI 테스트는 통과했으나 Starlette TestClient의 httpx 사용에 대한 deprecation warning 1개가 남는다. 현재 실패는 아니며 향후 테스트 클라이언트 의존성 전환을 검토한다. 브라우저 화면의 시각·상호작용 검증, 실제 JDK 17 실행, HTTPS 배포, 인증·운동·NLP 비즈니스 로직은 이번 검증에 포함하지 않는다.

실제 실행한 주요 명령은 java/node/python/docker 버전 확인, Docker Desktop 시작, npm install, python -m venv 및 pip install, Gradle test·bootJar, pytest, npm run build, Compose config --quiet·up -d --build, docker ps·exec, check-health.ps1, 장애 검증용 docker stop/start 및 검증 후 stop이다. Git은 status·diff·ls-files의 읽기 전용 확인만 실행했고 Commit/Push는 하지 않았다.

## 2026-10-28 생성·수정 파일

신규 파일 43개는 다음과 같다. 이전 작업에서 생성된 2026-10-28 Study·Meeting·Journal·1차 범위 문서는 이 목록에 포함하지 않는다.

```text
.env.example
ai-service/.dockerignore
ai-service/Dockerfile
ai-service/app/__init__.py
ai-service/app/main.py
ai-service/pyproject.toml
ai-service/requirements-dev.txt
ai-service/requirements.txt
ai-service/tests/test_health.py
backend/.dockerignore
backend/Dockerfile
backend/build.gradle
backend/gradle/wrapper/gradle-wrapper.jar
backend/gradle/wrapper/gradle-wrapper.properties
backend/gradlew
backend/gradlew.bat
backend/settings.gradle
backend/src/main/java/com/ailoveworkout/WorkoutApplication.java
backend/src/main/java/com/ailoveworkout/config/AiClientConfig.java
backend/src/main/java/com/ailoveworkout/config/SecurityConfig.java
backend/src/main/java/com/ailoveworkout/health/AiHealthClient.java
backend/src/main/java/com/ailoveworkout/health/DependencyHealthService.java
backend/src/main/java/com/ailoveworkout/health/HealthController.java
backend/src/main/resources/application.yml
backend/src/test/java/com/ailoveworkout/health/AiHealthClientTest.java
backend/src/test/java/com/ailoveworkout/health/DependencyHealthServiceTest.java
backend/src/test/java/com/ailoveworkout/health/HealthControllerTest.java
docs/architecture/portfolio-mvp-api.md
docs/architecture/portfolio-mvp-domain-model.md
docs/architecture/portfolio-mvp-erd.md
docs/development.md
frontend/.dockerignore
frontend/Dockerfile
frontend/index.html
frontend/package-lock.json
frontend/package.json
frontend/src/main.tsx
frontend/src/style.css
frontend/src/vite-env.d.ts
frontend/tsconfig.json
frontend/vite.config.ts
infra/compose.yaml
scripts/check-health.ps1
```

기존 파일은 README.md, frontend/README.md, backend/README.md, ai-service/README.md, infra/README.md, scripts/README.md의 실행 안내만 수정했다. 기존 파일 삭제는 없고 요구사항·ADR·Project Rules·AGENTS 및 작업 시작 문서의 해시가 작업 전과 동일함을 확인했다.
