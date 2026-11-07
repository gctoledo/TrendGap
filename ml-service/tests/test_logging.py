"""Tests for the ECS-style JSON logging (T059)."""

import io
import json
import logging
from collections.abc import Iterator
from typing import Any

import pytest
from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider
from trendgap_ml.logging import JsonFormatter, configure_logging

REQUIRED_KEYS = {"@timestamp", "log.level", "log.logger", "service.name", "message"}
ALLOWED_KEYS = REQUIRED_KEYS | {"trace.id", "span.id", "error.stack_trace"}


def _make_logger(stream: io.StringIO, name: str = "trendgap.test") -> logging.Logger:
    handler = logging.StreamHandler(stream)
    handler.setFormatter(JsonFormatter())
    logger = logging.getLogger(name)
    logger.handlers = [handler]
    logger.setLevel(logging.DEBUG)
    logger.propagate = False
    return logger


def _lines(stream: io.StringIO) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    for line in stream.getvalue().splitlines():
        parsed = json.loads(line)
        assert isinstance(parsed, dict)
        out.append(parsed)
    return out


@pytest.fixture
def stream() -> io.StringIO:
    return io.StringIO()


@pytest.fixture
def restore_root() -> Iterator[None]:
    root = logging.getLogger()
    handlers, level = list(root.handlers), root.level
    yield
    root.handlers = handlers
    root.setLevel(level)


def test_each_record_is_one_valid_json_line_with_required_keys(stream: io.StringIO) -> None:
    logger = _make_logger(stream)
    logger.info("first")
    logger.warning("second")
    assert len(stream.getvalue().splitlines()) == 2
    for entry in _lines(stream):
        assert entry.keys() >= REQUIRED_KEYS


def test_level_logger_message_and_service_name_are_correct(stream: io.StringIO) -> None:
    logger = _make_logger(stream, "trendgap.sample")
    logger.warning("hello")
    entry = _lines(stream)[0]
    assert entry["log.level"] == "WARNING"
    assert entry["log.logger"] == "trendgap.sample"
    assert entry["message"] == "hello"
    assert entry["service.name"] == "ml-service"


def test_custom_service_name_is_used() -> None:
    out = io.StringIO()
    handler = logging.StreamHandler(out)
    handler.setFormatter(JsonFormatter("other"))
    logger = logging.getLogger("trendgap.custom")
    logger.handlers = [handler]
    logger.propagate = False
    logger.error("x")
    assert _lines(out)[0]["service.name"] == "other"


def test_timestamp_is_iso8601_utc(stream: io.StringIO) -> None:
    from datetime import datetime, timedelta

    _make_logger(stream).info("t")
    parsed = datetime.fromisoformat(_lines(stream)[0]["@timestamp"].replace("Z", "+00:00"))
    assert parsed.utcoffset() == timedelta(0)


def test_message_args_are_formatted(stream: io.StringIO) -> None:
    _make_logger(stream).info("value %s and %d", "abc", 7)
    assert _lines(stream)[0]["message"] == "value abc and 7"


def test_trace_and_span_ids_present_inside_active_span(stream: io.StringIO) -> None:
    logger = _make_logger(stream)
    tracer = TracerProvider().get_tracer("test")
    with tracer.start_as_current_span("op") as span:
        logger.info("inside")
        ctx = span.get_span_context()
    entry = _lines(stream)[0]
    assert entry["trace.id"] == format(ctx.trace_id, "032x")
    assert entry["span.id"] == format(ctx.span_id, "016x")
    assert len(entry["trace.id"]) == 32
    assert len(entry["span.id"]) == 16


def test_trace_and_span_ids_absent_outside_span(stream: io.StringIO) -> None:
    assert not trace.get_current_span().get_span_context().is_valid
    _make_logger(stream).info("outside")
    entry = _lines(stream)[0]
    assert "trace.id" not in entry
    assert "span.id" not in entry


def test_extra_secret_fields_are_never_serialized(stream: io.StringIO) -> None:
    logger = _make_logger(stream)
    logger.info(
        "login",
        extra={"password": "hunter2", "authorization": "Bearer abc", "custom": "v"},
    )
    raw = stream.getvalue()
    assert "hunter2" not in raw
    assert "Bearer abc" not in raw
    entry = _lines(stream)[0]
    assert entry.keys() <= ALLOWED_KEYS
    assert "password" not in entry
    assert "authorization" not in entry


def test_exception_goes_only_to_error_stack_trace(stream: io.StringIO) -> None:
    logger = _make_logger(stream)
    try:
        raise ValueError("boom")
    except ValueError:
        logger.exception("failed")
    entry = _lines(stream)[0]
    assert "ValueError" in entry["error.stack_trace"]
    assert entry.keys() <= ALLOWED_KEYS


def test_configure_logging_twice_leaves_single_stdout_handler(restore_root: None) -> None:
    configure_logging("INFO")
    configure_logging("DEBUG")
    root = logging.getLogger()
    assert len(root.handlers) == 1
    handler = root.handlers[0]
    assert isinstance(handler, logging.StreamHandler)
    assert isinstance(handler.formatter, JsonFormatter)
    assert root.level == logging.DEBUG
