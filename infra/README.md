# Infrastructure

Docker, Nginx, AWS deployment, monitoring, and environment configuration.

이번 단계에는 개발용 Compose와 PostgreSQL 17만 구성하며 Redis·Nginx·AWS는 추가하지 않는다. 루트에서 환경 변수를 준비한 뒤 docker compose -f infra/compose.yaml up -d --build를 실행한다. 기존 데이터가 있는 볼륨을 재초기화하거나 삭제하지 않는다. 상세 환경 파일·실행 방법은 [개발 환경 안내](../docs/development.md)를 따른다.
