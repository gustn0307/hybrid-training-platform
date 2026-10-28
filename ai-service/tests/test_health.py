"""실제 네트워크 없이 헬스 응답과 허용 메서드, 미구현 경로의 HTTP 계약을 검증한다."""

from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health() -> None:
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "UP", "service": "ai-service"}


def test_health_is_read_only() -> None:
    assert client.post("/health").status_code == 405


def test_business_routes_are_not_implemented() -> None:
    assert client.post("/parse", json={"text": "test"}).status_code == 404
