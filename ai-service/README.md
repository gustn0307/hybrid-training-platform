# AI Service

Python and FastAPI service for workout parsing, analysis, recommendations, and future AI agent features.

현재는 GET /health만 구현되어 있다. 자연어 구조화·외부 LLM·사용자 토큰 관리는 구현하지 않았다. Python 3.12 가상환경에서 requirements-dev.txt 설치 후 python -m pytest, python -m uvicorn app.main:app로 검증·실행한다. 상세 순서는 [개발 환경 안내](../docs/development.md)를 따른다.
