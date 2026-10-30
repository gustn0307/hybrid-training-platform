# Scripts

Development, build, deployment, and maintenance scripts.

check-health.ps1은 Spring Boot·FastAPI·DB 연결 및 Vite 프록시를 통해 서비스 간 통신을 확인한다. 실제 서비스를 먼저 실행해야 하며 Mock 테스트를 대체하지 않는다. 실행 순서는 [개발 환경 안내](../docs/development.md)를 따른다.

verify-auth.ps1은 별도 Compose 프로젝트에 네 서비스를 실행하고 PostgreSQL 기반 AUTH / USER 자동 테스트·API 흐름·재시작 후 JWT 무효화를 검증한다. 임시 자격정보는 환경 변수로만 전달하고 기존 컨테이너·볼륨을 삭제하지 않는다. 실행 후 컨테이너는 브라우저 확인을 위해 남긴다.
