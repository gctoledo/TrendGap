"""T018: fail-fast configuration contract for the ML service.

Expected implementation (T019), in `trendgap_ml.settings`:
- `Settings` (pydantic-settings, env_prefix `TRENDGAP_ML_`) with `port`, `log_level`,
  `otlp_endpoint` (alias `OTEL_EXPORTER_OTLP_ENDPOINT`).
- `format_validation_error(exc: pydantic.ValidationError) -> str`: names the environment
  variable and the rule, never the `input_value`.
`python -m trendgap_ml` must exit with code != 0 on invalid configuration.
"""

import subprocess
import sys

import pytest
from pydantic import ValidationError
from trendgap_ml.settings import Settings, format_validation_error

SECRET_MARKER = "zz-leaked-value-123"
ENV_VARS = ("TRENDGAP_ML_PORT", "TRENDGAP_ML_LOG_LEVEL", "OTEL_EXPORTER_OTLP_ENDPOINT")


@pytest.fixture(autouse=True)
def clean_env(monkeypatch: pytest.MonkeyPatch) -> None:
    for name in ENV_VARS:
        monkeypatch.delenv(name, raising=False)


def test_defaults_are_port_8000_info_and_no_otlp_endpoint() -> None:
    settings = Settings()
    assert settings.port == 8000
    assert settings.log_level == "INFO"
    assert settings.otlp_endpoint is None


def test_reads_valid_values_from_environment(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("TRENDGAP_ML_PORT", "9090")
    monkeypatch.setenv("TRENDGAP_ML_LOG_LEVEL", "DEBUG")
    monkeypatch.setenv("OTEL_EXPORTER_OTLP_ENDPOINT", "http://collector:4318")
    settings = Settings()
    assert settings.port == 9090
    assert settings.log_level == "DEBUG"
    assert settings.otlp_endpoint is not None
    assert str(settings.otlp_endpoint).startswith("http://collector:4318")


@pytest.mark.parametrize("port", ["1", "65535"])
def test_accepts_port_boundaries(monkeypatch: pytest.MonkeyPatch, port: str) -> None:
    monkeypatch.setenv("TRENDGAP_ML_PORT", port)
    assert Settings().port == int(port)


@pytest.mark.parametrize("port", ["0", "65536", "-1", "abc", "80.5"])
def test_rejects_invalid_port(monkeypatch: pytest.MonkeyPatch, port: str) -> None:
    monkeypatch.setenv("TRENDGAP_ML_PORT", port)
    with pytest.raises(ValidationError):
        Settings()


@pytest.mark.parametrize("level", ["DEBUG", "INFO", "WARNING", "ERROR"])
def test_accepts_allowed_log_levels(monkeypatch: pytest.MonkeyPatch, level: str) -> None:
    monkeypatch.setenv("TRENDGAP_ML_LOG_LEVEL", level)
    assert Settings().log_level == level


@pytest.mark.parametrize("level", ["TRACE", "CRITICAL", "info", ""])
def test_rejects_invalid_log_level(monkeypatch: pytest.MonkeyPatch, level: str) -> None:
    monkeypatch.setenv("TRENDGAP_ML_LOG_LEVEL", level)
    with pytest.raises(ValidationError):
        Settings()


@pytest.mark.parametrize("endpoint", ["not-a-url", "localhost:4318", "/relative/path"])
def test_rejects_non_absolute_otlp_endpoint(monkeypatch: pytest.MonkeyPatch, endpoint: str) -> None:
    monkeypatch.setenv("OTEL_EXPORTER_OTLP_ENDPOINT", endpoint)
    with pytest.raises(ValidationError):
        Settings()


def test_empty_otlp_endpoint_counts_as_absent(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("OTEL_EXPORTER_OTLP_ENDPOINT", "")
    assert Settings().otlp_endpoint is None


def test_error_message_cites_port_variable_and_rule_without_value(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("TRENDGAP_ML_PORT", SECRET_MARKER)
    with pytest.raises(ValidationError) as info:
        Settings()
    message = format_validation_error(info.value)
    assert "TRENDGAP_ML_PORT" in message
    assert "input_value" not in message
    assert SECRET_MARKER not in message


def test_error_message_cites_log_level_variable_without_value(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("TRENDGAP_ML_LOG_LEVEL", SECRET_MARKER)
    with pytest.raises(ValidationError) as info:
        Settings()
    message = format_validation_error(info.value)
    assert "TRENDGAP_ML_LOG_LEVEL" in message
    assert "input_value" not in message
    assert SECRET_MARKER not in message


def test_error_message_cites_otlp_variable_without_value(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("OTEL_EXPORTER_OTLP_ENDPOINT", SECRET_MARKER)
    with pytest.raises(ValidationError) as info:
        Settings()
    message = format_validation_error(info.value)
    assert "OTEL_EXPORTER_OTLP_ENDPOINT" in message
    assert "input_value" not in message
    assert SECRET_MARKER not in message


def test_main_exits_nonzero_with_message_but_without_value_on_invalid_config(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("TRENDGAP_ML_PORT", SECRET_MARKER)
    result = subprocess.run(
        [sys.executable, "-m", "trendgap_ml"],
        capture_output=True,
        text=True,
        timeout=30,
        check=False,
    )
    output = result.stdout + result.stderr
    assert result.returncode != 0
    assert "TRENDGAP_ML_PORT" in output
    assert SECRET_MARKER not in output
    assert "input_value" not in output
