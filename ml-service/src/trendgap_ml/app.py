from fastapi import FastAPI

from trendgap_ml.api import health


def create_app() -> FastAPI:
    app = FastAPI(title="TrendGap ML service", version="0.1.0")
    app.include_router(health.router)
    return app
