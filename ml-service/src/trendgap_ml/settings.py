"""Fail-fast configuration for the ML service (never echoes configuration values)."""

from typing import Annotated, Literal

from pydantic import AnyUrl, BeforeValidator, Field, UrlConstraints, ValidationError
from pydantic_settings import BaseSettings, SettingsConfigDict

ENV_PREFIX = "TRENDGAP_ML_"
OTLP_ENV_VAR = "OTEL_EXPORTER_OTLP_ENDPOINT"

_FIELD_ENV_VARS = {
    "port": f"{ENV_PREFIX}PORT",
    "log_level": f"{ENV_PREFIX}LOG_LEVEL",
    "otlp_endpoint": OTLP_ENV_VAR,
}

_AbsoluteUrl = Annotated[AnyUrl, UrlConstraints(host_required=True)]


def _empty_to_none(value: object) -> object:
    return None if value == "" else value


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix=ENV_PREFIX, extra="ignore")

    port: int = Field(default=8000, ge=1, le=65535)
    log_level: Literal["DEBUG", "INFO", "WARNING", "ERROR"] = "INFO"
    otlp_endpoint: Annotated[_AbsoluteUrl | None, BeforeValidator(_empty_to_none)] = Field(
        default=None, validation_alias=OTLP_ENV_VAR
    )


def format_validation_error(exc: ValidationError) -> str:
    """Lists each invalid variable with the broken rule, never the offending value."""
    lines = ["Invalid configuration:"]
    for error in exc.errors(include_input=False, include_url=False):
        field = str(error["loc"][0]) if error["loc"] else "?"
        variable = _FIELD_ENV_VARS.get(field, field)
        lines.append(f"- {variable}: {error['msg']}")
    return "\n".join(lines)
