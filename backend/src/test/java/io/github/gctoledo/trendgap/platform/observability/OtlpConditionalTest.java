package io.github.gctoledo.trendgap.platform.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.registry.otlp.OtlpMeterRegistry;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.otlp.OtlpMetricsExportAutoConfiguration;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpTracingAutoConfiguration;
import org.springframework.boot.opentelemetry.autoconfigure.logging.otlp.OtlpLoggingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * OTLP export (traces, logs, metrics) must only be created when OTEL_EXPORTER_OTLP_ENDPOINT is set
 * to a non-blank value. The runner applies {@link OtlpExportSwitch} to the environment and then
 * loads Boot's own OTLP auto-configurations.
 */
class OtlpConditionalTest {

  private static final String ENDPOINT = "http://collector:4318";

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withInitializer(ctx ->
              new OtlpExportSwitch()
                  .postProcessEnvironment(ctx.getEnvironment(), new SpringApplication()))
          .withConfiguration(
              AutoConfigurations.of(
                  MetricsAutoConfiguration.class,
                  OtlpTracingAutoConfiguration.class,
                  OtlpLoggingAutoConfiguration.class,
                  OtlpMetricsExportAutoConfiguration.class));

  /** Properties Boot's OTEL env mapping would derive from OTEL_EXPORTER_OTLP_ENDPOINT. */
  private static String[] withEndpoint() {
    return new String[] {
      "OTEL_EXPORTER_OTLP_ENDPOINT=" + ENDPOINT,
      "management.opentelemetry.tracing.export.otlp.endpoint=" + ENDPOINT + "/v1/traces",
      "management.opentelemetry.logging.export.otlp.endpoint=" + ENDPOINT + "/v1/logs",
      "management.otlp.metrics.export.url=" + ENDPOINT + "/v1/metrics"
    };
  }

  @Test
  void createsNoOtlpExporterWhenEndpointIsAbsent() {
    runner.run(context -> {
      assertThat(context).doesNotHaveBean(OtlpHttpSpanExporter.class);
      assertThat(context).doesNotHaveBean(OtlpHttpLogRecordExporter.class);
      assertThat(context).doesNotHaveBean(OtlpMeterRegistry.class);
    });
  }

  @Test
  void createsNoOtlpExporterWhenEndpointIsBlank() {
    runner
        .withPropertyValues("OTEL_EXPORTER_OTLP_ENDPOINT=")
        .run(context -> {
          assertThat(context).doesNotHaveBean(OtlpHttpSpanExporter.class);
          assertThat(context).doesNotHaveBean(OtlpHttpLogRecordExporter.class);
          assertThat(context).doesNotHaveBean(OtlpMeterRegistry.class);
        });
  }

  @Test
  void createsNoOtlpExporterWhenEndpointIsOnlyWhitespace() {
    runner
        .withPropertyValues("OTEL_EXPORTER_OTLP_ENDPOINT=   ")
        .run(context -> assertThat(context).doesNotHaveBean(OtlpHttpSpanExporter.class));
  }

  @Test
  void createsSpanExporterWhenEndpointIsConfigured() {
    runner
        .withPropertyValues(withEndpoint())
        .run(context -> assertThat(context).hasSingleBean(OtlpHttpSpanExporter.class));
  }

  @Test
  void createsLogRecordExporterWhenEndpointIsConfigured() {
    runner
        .withPropertyValues(withEndpoint())
        .run(context -> assertThat(context).hasSingleBean(OtlpHttpLogRecordExporter.class));
  }

  @Test
  void createsOtlpMeterRegistryWhenEndpointIsConfigured() {
    runner
        .withPropertyValues(withEndpoint())
        .run(context -> assertThat(context).hasSingleBean(OtlpMeterRegistry.class));
  }
}
