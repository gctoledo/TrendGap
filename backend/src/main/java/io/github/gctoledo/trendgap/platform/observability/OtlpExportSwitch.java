package io.github.gctoledo.trendgap.platform.observability;

import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/** Turns OTLP export off for traces, logs and metrics unless OTEL_EXPORTER_OTLP_ENDPOINT is set. */
public class OtlpExportSwitch implements EnvironmentPostProcessor {

  static final String ENDPOINT = "OTEL_EXPORTER_OTLP_ENDPOINT";

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    String endpoint = environment.getProperty(ENDPOINT);
    if (endpoint != null && !endpoint.isBlank()) {
      return;
    }
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "trendgapOtlpExportDisabled",
                Map.of(
                    "management.tracing.export.otlp.enabled", "false",
                    "management.logging.export.otlp.enabled", "false",
                    "management.otlp.metrics.export.enabled", "false")));
  }
}
