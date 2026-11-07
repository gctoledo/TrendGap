"""ECS-style JSON logging on the stdlib: one line per record, fixed field set only."""

import json
import logging
import sys
from datetime import UTC, datetime

from opentelemetry import trace


class JsonFormatter(logging.Formatter):
    """Emits only a fixed set of fields, so `extra` data (possible secrets) never leaks."""

    def __init__(self, service_name: str = "ml-service") -> None:
        super().__init__()
        self._service_name = service_name

    def format(self, record: logging.LogRecord) -> str:
        timestamp = datetime.fromtimestamp(record.created, UTC)
        payload: dict[str, str] = {
            "@timestamp": timestamp.isoformat(timespec="milliseconds").replace("+00:00", "Z"),
            "log.level": record.levelname,
            "log.logger": record.name,
            "service.name": self._service_name,
            "message": record.getMessage(),
        }
        context = trace.get_current_span().get_span_context()
        if context.is_valid:
            payload["trace.id"] = trace.format_trace_id(context.trace_id)
            payload["span.id"] = trace.format_span_id(context.span_id)
        if record.exc_info:
            payload["error.stack_trace"] = self.formatException(record.exc_info)
        return json.dumps(payload, ensure_ascii=False)


def configure_logging(level: str) -> None:
    """Installs a single stdout JSON handler on the root logger (idempotent)."""
    root = logging.getLogger()
    for handler in list(root.handlers):
        root.removeHandler(handler)
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(JsonFormatter())
    root.addHandler(handler)
    root.setLevel(level)
