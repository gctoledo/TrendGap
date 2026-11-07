"""OpenTelemetry wiring: tracing, Prometheus /metrics and optional OTLP export."""

import logging
import time
from collections.abc import Awaitable, Callable
from dataclasses import dataclass

from fastapi import FastAPI, Request, Response
from opentelemetry.exporter.otlp.proto.http._log_exporter import OTLPLogExporter
from opentelemetry.exporter.otlp.proto.http.metric_exporter import OTLPMetricExporter
from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
from opentelemetry.exporter.prometheus import PrometheusMetricReader
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
from opentelemetry.sdk._logs import LoggerProvider, LoggingHandler
from opentelemetry.sdk._logs.export import BatchLogRecordProcessor
from opentelemetry.sdk.metrics import MeterProvider
from opentelemetry.sdk.metrics.export import MetricReader, PeriodicExportingMetricReader
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest

SERVICE_NAME = "ml-service"
ACCESS_LOGGER = logging.getLogger("trendgap_ml.access")


def otlp_enabled(endpoint: str | None) -> bool:
    """OTLP export is on only when the endpoint is set and not blank."""
    return endpoint is not None and endpoint.strip() != ""


@dataclass(frozen=True)
class _Providers:
    tracer: TracerProvider
    meter: MeterProvider


_providers: _Providers | None = None


def _build_providers(endpoint: str | None) -> _Providers:
    resource = Resource.create({"service.name": SERVICE_NAME})
    tracer = TracerProvider(resource=resource)
    readers: list[MetricReader] = [PrometheusMetricReader()]
    if endpoint is not None and otlp_enabled(endpoint):
        base = endpoint.strip().rstrip("/")
        tracer.add_span_processor(BatchSpanProcessor(OTLPSpanExporter(f"{base}/v1/traces")))
        readers.append(PeriodicExportingMetricReader(OTLPMetricExporter(f"{base}/v1/metrics")))
        logger_provider = LoggerProvider(resource=resource)
        logger_provider.add_log_record_processor(
            BatchLogRecordProcessor(OTLPLogExporter(f"{base}/v1/logs"))
        )
        logging.getLogger().addHandler(
            LoggingHandler(level=logging.NOTSET, logger_provider=logger_provider)
        )
    meter = MeterProvider(resource=resource, metric_readers=readers)
    return _Providers(tracer, meter)


def setup_telemetry(app: FastAPI, endpoint: str | None) -> None:
    """Instruments the app. Providers are built once per process and passed explicitly."""
    global _providers
    if _providers is None:
        _providers = _build_providers(endpoint)

    @app.middleware("http")
    async def log_request(
        request: Request, call_next: Callable[[Request], Awaitable[Response]]
    ) -> Response:
        start = time.perf_counter()
        response = await call_next(request)
        ACCESS_LOGGER.info(
            "%s %s %d (%.1f ms)",
            request.method,
            request.url.path,
            response.status_code,
            (time.perf_counter() - start) * 1000,
        )
        return response

    @app.get("/metrics", include_in_schema=False)
    def metrics() -> Response:
        return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)

    # Added last so the OTel middleware is the outermost: the access log runs inside the span.
    FastAPIInstrumentor.instrument_app(
        app,
        tracer_provider=_providers.tracer,
        meter_provider=_providers.meter,
        excluded_urls="/metrics$",
    )
