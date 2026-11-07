from fastapi.testclient import TestClient

from trendgap_ml.app import create_app


def test_health_returns_200_with_status_up() -> None:
    client = TestClient(create_app())

    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "UP"}
