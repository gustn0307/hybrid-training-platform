# AI Love Workout Development Guide

## Project Overview

AI Love Workout is an AI-powered hybrid training platform supporting:

- Running
- Weight training
- Calisthenics
- AI-assisted workout recording
- Personalized training analysis
- Future wearable-device synchronization

## Repository Structure

- `frontend`: React and TypeScript frontend
- `backend`: Spring Boot API and authentication server
- `ai-service`: Python and FastAPI AI service
- `infra`: Docker, Nginx, CI/CD, and deployment configuration
- `docs`: Requirements, architecture, ERD, and API documentation
- `scripts`: Development and deployment scripts

## Development Rules

1. Analyze the existing repository before modifying files.
2. Do not implement unrelated features.
3. Keep changes limited to the requested directories.
4. Do not add dependencies without explaining why they are needed.
5. Never commit secrets, tokens, passwords, certificates, or production configuration.
6. Use environment variables for external configuration.
7. Add or update tests when business logic changes.
8. Run relevant tests, builds, linters, and type checks after modifications.
9. Report all changed files, executed commands, and test results.
10. Preserve backward compatibility unless a breaking change is explicitly approved.
11. Do not delete existing files without explicit approval.
12. Do not execute destructive Git, Docker, database, or operating-system commands without explicit approval.

## Architecture Rules

- Spring Boot is the main public API server.
- Spring Security owns user authentication and authorization.
- FastAPI must not issue or manage user authentication tokens.
- The frontend communicates with Spring Boot.
- Spring Boot communicates with FastAPI through an internal service interface.
- All server timestamps are stored in UTC.
- The user's timezone is stored separately.
- External wearable data must preserve its original payload.
- User-corrected workout data must not overwrite imported raw data.
- Sensitive health and authentication data must not be written to application logs.
- External integrations must be isolated behind provider-specific adapters.

## Initial Technology Direction

- React
- TypeScript
- Vite
- Java 17
- Spring Boot
- Gradle
- Python
- FastAPI
- PostgreSQL
- Redis
- Docker
- AWS
- GitHub Actions