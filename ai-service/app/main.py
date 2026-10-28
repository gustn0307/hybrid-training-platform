"""내부 AI 서비스의 프로세스 헬스를 제공한다. 사용자 인증이나 외부 LLM 호출은 수행하지 않는다."""

from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI(title="AI Love Workout internal AI service", version="0.1.0")


class HealthResponse(BaseModel):
    status: str
    service: str


# Docstring의 \f 뒤 설명은 OpenAPI에 노출하지 않아 기존 API 문서 응답을 유지한다.
@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    """
    \f
    프로세스 응답만 확인하며 실제 자연어 구조화나 외부 AI 연동의 성공을 보장하지 않는다.
    """
    return HealthResponse(status="UP", service="ai-service")
