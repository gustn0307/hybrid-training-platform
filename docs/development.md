# 포트폴리오 MVP 개발 환경

## 현재 구현 범위

React 기본 화면, Spring Boot·FastAPI 헬스 API, PostgreSQL 연결 점검과 개발용 Compose 골격이다. 회원가입·운동 기록·추천·NLP·도메인 테이블은 아직 없다. 헬스 응답 성공은 비즈니스 기능 또는 외부 LLM 성공을 뜻하지 않는다.

## 도구와 선택 이유

* JDK 21: 현재 설치된 LTS JDK를 사용하여 추가 JDK 설치를 피한다. Gradle의 --release 17로 기존 Java 17 언어·바이트코드·표준 API 기준을 유지한다. 실제 실행 검증은 JDK 21이며 JDK 17에서의 실행은 별도 검증 대상이다.
* Spring Boot 3.5.16, Gradle 8.14.3: Java 17 기준과 설치된 JDK 21을 지원하는 조합이다. [Spring Boot 호환성](https://docs.spring.io/spring-boot/3.5/system-requirements.html), [Gradle JVM 호환성](https://docs.gradle.org/current/userguide/compatibility.html)을 근거로 선택했다. Spring Security·JPA·JDBC·테스트 버전은 Boot BOM으로 맞춘다.
* React 19.3.1, Vite 8.3.5, TypeScript 7.0.2: 기본 UI와 타입 검사·개발 프록시·번들에 사용한다. @vitejs/plugin-react 6.1.2와 React 타입 19.3.0을 사용한다. Node 22.12 이상을 요구하며 현재 Node 24.19.0으로 검증한다. 정확한 의존성은 package-lock.json으로 고정한다.
* Python 3.12, FastAPI 0.162.0, Uvicorn 0.54.0: 내부 HTTP 서비스의 실행에 필요하다. pytest 9.2.0·httpx 0.28.1은 테스트용이며 컨테이너 실행 이미지에는 설치하지 않는다.
* PostgreSQL 17: 로컬 DB를 Compose로 실행한다. 이미지의 17 태그는 해당 메이저의 패치 이미지를 받는다. 실제 실행 버전은 SELECT version()으로 확인한다.

이번 단계에는 OpenAI SDK, Redis, H2, 프론트엔드 상태 관리 라이브러리, Nginx와 AWS 도구가 필요하지 않아 추가하지 않는다.

## 전체 Compose 실행

저장소 루트의 PowerShell에서 실행한다. Docker Desktop의 Linux 컨테이너 엔진이 준비되어 있어야 한다.

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
# .env의 POSTGRES_PASSWORD를 로컬에서 직접 설정한다. 기존 .env가 있다면 덮어쓰지 않는다.
docker compose --env-file .env -f infra/compose.yaml config --quiet
docker compose --env-file .env -f infra/compose.yaml up -d --build
docker compose --env-file .env -f infra/compose.yaml ps
./scripts/check-health.ps1
```

환경 예제에는 실제 비밀번호·키가 없다. .env는 Git 제외 대상이며 필수 비밀번호가 없으면 Compose가 실패한다. 설정 확인에는 비밀값이 출력되지 않는 config --quiet를 사용한다. PostgreSQL 볼륨의 기존 계정은 환경 변수 변경만으로 갱신되지 않으므로 기존 볼륨에 다른 비밀번호를 적용하거나 데이터를 삭제하지 않는다.

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

백엔드 자동 테스트는 헬스 API 공개 범위·기타 요청 차단, DB 핸들 확인·반납, 내부 AI 계약·장애 처리를 검증한다. DB와 AI를 모킹하므로 실제 PostgreSQL 연결 성공으로 취급하지 않는다. Python 테스트도 TestClient 기반이며 실제 네트워크 검증은 check-health.ps1로 별도 수행한다. 프론트엔드 build는 타입 검사와 번들 생성이며 브라우저 상호작용 테스트가 아니다.

Gradle Wrapper는 고정 배포판과 SHA-256을 사용한다. 최초 실행과 npm·pip 설치, Docker 이미지 빌드에는 네트워크 접근이 필요하다. DB가 없거나 비밀번호가 잘못되면 정상 애플리케이션 준비로 안내하지 않는다.

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

## 이번 단계 생성·수정 파일

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
