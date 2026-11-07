import os

from fastapi import FastAPI

from trendgap_ml.api import health
from trendgap_ml.settings import OTLP_ENV_VAR
from trendgap_ml.telemetry import setup_telemetry


def create_app() -> FastAPI:
    app = FastAPI(title="TrendGap ML service", version="0.1.0")
    app.include_router(health.router)
    setup_telemetry(app, os.environ.get(OTLP_ENV_VAR))
    return app
