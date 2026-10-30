# AI Love Workout

AI Love Workout is an AI-powered hybrid training platform for running,
weight training, and calisthenics.

## Development Status

현재는 러닝·맨몸운동 1차 MVP를 위한 AUTH + USER 구현 단계입니다. React와 Spring Boot·PostgreSQL로 회원가입·로그인·운동 프로필 최초 설정·조회·부분 수정·로그아웃을 연결하고 Spring Boot·FastAPI 헬스 API와 DB 연결 점검을 유지합니다. 운동 기록·추천은 아직 구현하지 않았습니다.

* [개발 환경 실행·테스트](docs/development.md)
* [1차 구현 범위](docs/requirements/portfolio-mvp-scope-2026-11-01.md)
* [최소 도메인 모델](docs/architecture/portfolio-mvp-domain-model.md)
* [최소 ERD](docs/architecture/portfolio-mvp-erd.md)
* [최소 API 설계](docs/architecture/portfolio-mvp-api.md)

## Project Goal

AI Love Workout helps users record, analyze, and manage workouts with minimal manual input.

The initial version will support manual workout entry and AI-assisted natural-language entry.

Future versions will support automatic workout synchronization through wearable and health platforms such as:

- Apple Health
- Android Health Connect
- Apple Watch
- Amazfit and Zepp
- Garmin
- Galaxy Watch

## Supported Training Types

- Running
- Weight training
- Calisthenics

## Core Features

- User authentication
- Manual workout entry
- Natural-language workout entry
- Workout history
- Weekly and monthly statistics
- Goal management
- AI workout analysis
- AI training recommendations
- Future wearable synchronization
- User correction of imported workout data

## Planned Technology Stack

### Frontend

- React
- TypeScript
- Vite
- React Router
- TanStack Query

### Backend

- Java 17
- Spring Boot
- Gradle
- Spring Security
- JWT
- Spring Data JPA
- PostgreSQL
- Redis

### AI Service

- Python
- FastAPI
- OpenAI API
- Future AI agent architecture

### Infrastructure

- Docker
- Docker Compose
- Nginx
- AWS
- GitHub Actions
- Cloudflare

## Repository Structure

```text
frontend/      React web application
backend/       Spring Boot API server
ai-service/    Python AI service
infra/         Docker and deployment configuration
docs/          Requirements and architecture documents
scripts/       Development and deployment scripts
```
