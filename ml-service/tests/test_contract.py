from pathlib import Path
from typing import Any

import schemathesis
import yaml
from hypothesis import HealthCheck, settings

from trendgap_ml.app import create_app

CONTRACT = Path(__file__).resolve().parent.parent.parent / "contracts" / "ml-service.openapi.yaml"
HTTP_METHODS = {"get", "put", "post", "delete", "patch", "head", "options", "trace"}
IGNORED_PATHS = {"/docs", "/redoc", "/openapi.json", "/docs/oauth2-redirect"}

app = create_app()
schema = schemathesis.openapi.from_path(CONTRACT)


@schema.parametrize()
@settings(max_examples=25, suppress_health_check=list(HealthCheck), deadline=None)
def test_app_conforms_to_openapi_contract(case: Any) -> None:
    case.call_and_validate(app=app)


def test_app_routes_match_contract_paths() -> None:
    contract = yaml.safe_load(CONTRACT.read_text(encoding="utf-8"))
    expected = {
        (method.upper(), path)
        for path, item in contract["paths"].items()
        for method in item
        if method in HTTP_METHODS
    }
    # Routers included lazily by FastAPI are not in app.routes; the generated schema lists them all.
    actual = {
        (method.upper(), path)
        for path, item in create_app().openapi()["paths"].items()
        for method in item
        if method in HTTP_METHODS and path not in IGNORED_PATHS
    }

    assert actual == expected
