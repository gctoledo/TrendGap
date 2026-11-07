import io
import json
import logging
import re
from collections.abc import Iterator
from typing import Any

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient
from trendgap_ml.logging import JsonFormatter
from trendgap_ml.telemetry import otlp_enabled

from trendgap_ml.app import create_app

TRACE_ID = "4bf92f3577b34da6a3ce929d0e4e4736"
PARENT_SPAN_ID = "00f067aa0ba902b7"
TRACEPARENT = f"00-{TRACE_ID}-{PARENT_SPAN_ID}-01"
SECRET_HEADER = "Bearer s3cr3t-header-value"
SECRET_QUERY = "s3cr3t-query-value"


@pytest.fixture(scope="module")
def app() -> FastAPI:
    # OTel global providers can only be set once per process: build the app once.
    return create_app()


@pytest.fixture(scope="module")
def client(app: FastAPI) -> Iterator[TestClient]:
    with TestClient(app) as test_client:
        yield test_client


@pytest.fixture
def access_log() -> Iterator[io.StringIO]:
    stream = io.StringIO()
    handler = logging.StreamHandler(stream)
    handler.setFormatter(JsonFormatter())
    logger = logging.getLogger("trendgap_ml.access")
    previous_level = logger.level
    logger.addHandler(handler)
    logger.setLevel(logging.INFO)
    try:
        yield stream
    finally:
        logger.removeHandler(handler)
        logger.setLevel(previous_level)


def _log_lines(stream: io.StringIO) -> list[dict[str, Any]]:
    return [json.loads(line) for line in stream.getvalue().splitlines() if line.strip()]


def test_request_log_carries_trace_id_from_traceparent_header(
    client: TestClient, access_log: io.StringIO
) -> None:
    response = client.get("/health", headers={"traceparent": TRACEPARENT})

    assert response.status_code == 200
    lines = _log_lines(access_log)
    assert len(lines) == 1
    line = lines[0]
    assert line["trace.id"] == TRACE_ID
    assert re.fullmatch(r"[0-9a-f]{16}", line["span.id"])
    assert line["span.id"] != PARENT_SPAN_ID
    assert "GET /health" in line["message"]
    assert "200" in line["message"]


def test_request_without_traceparent_still_logs_a_generated_trace_id(
    client: TestClient, access_log: io.StringIO
) -> None:
    client.get("/health")

    line = _log_lines(access_log)[0]
    assert re.fullmatch(r"[0-9a-f]{32}", line["trace.id"])
    assert line["trace.id"] != TRACE_ID


def test_metrics_endpoint_exposes_request_count_and_duration(client: TestClient) -> None:
    client.get("/health")
    client.get("/health")

    response = client.get("/metrics")

    assert response.status_code == 200
    body = response.text
    base = r"http_server_(?:request_)?duration\w*"
    assert re.search(rf"^{base}_count\b.*\s[1-9][0-9.e+]*$", body, re.MULTILINE)
    assert re.search(rf"^{base}_sum\b", body, re.MULTILINE)


def test_log_line_contains_no_header_or_query_values(
    client: TestClient, access_log: io.StringIO
) -> None:
    client.get(
        f"/health?token={SECRET_QUERY}",
        headers={"traceparent": TRACEPARENT, "Authorization": SECRET_HEADER},
    )

    raw = access_log.getvalue()
    assert raw.strip()
    assert SECRET_QUERY not in raw
    assert "s3cr3t-header-value" not in raw
    assert SECRET_HEADER not in raw
    assert "Authorization" not in raw


@pytest.mark.parametrize("endpoint", [None, "", "   ", "\t\n"])
def test_otlp_is_disabled_without_endpoint(endpoint: str | None) -> None:
    assert otlp_enabled(endpoint) is False


@pytest.mark.parametrize("endpoint", ["http://localhost:4318", "https://otel.example.com:4317"])
def test_otlp_is_enabled_with_endpoint_url(endpoint: str) -> None:
    assert otlp_enabled(endpoint) is True
