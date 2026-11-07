package io.github.gctoledo.trendgap.platform.config;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

/** Reports invalid configuration naming the environment variable and the rule, never the value. */
public class ConfigFailureAnalyzer extends AbstractFailureAnalyzer<BindException> {

  private static final String PREFIX = "trendgap.";
  private static final String DISTINCT = "managementPortDistinct";

  private static final Map<String, String> RULES =
      Map.of(
          "db.url",
          "TRENDGAP_DB_URL: required, must start with jdbc:postgresql://",
          "db.username",
          "TRENDGAP_DB_USERNAME: required, must not be empty",
          "db.password",
          "TRENDGAP_DB_PASSWORD: required, must not be empty",
          "ml.base-url",
          "TRENDGAP_ML_BASE_URL: required, must be an absolute http or https URL",
          "ml.timeout",
          "TRENDGAP_ML_TIMEOUT: must be a duration between 100ms and 5s",
          "http.port",
          "TRENDGAP_HTTP_PORT: must be an integer between 1 and 65535",
          "management.port",
          "TRENDGAP_MANAGEMENT_PORT: must be an integer between 1 and 65535",
          "otel.endpoint",
          "OTEL_EXPORTER_OTLP_ENDPOINT: must be an absolute URL when set",
          DISTINCT,
          "TRENDGAP_MANAGEMENT_PORT: must differ from TRENDGAP_HTTP_PORT");

  @Override
  protected @Nullable FailureAnalysis analyze(Throwable rootFailure, BindException cause) {
    Set<String> keys = new LinkedHashSet<>();
    for (Throwable t = cause; t != null; t = t.getCause()) {
      if (t instanceof BindException bind && bind.getProperty() != null) {
        keys.add(normalize(bind.getProperty().getName().toString()));
      }
      if (t instanceof BindValidationException validation) {
        for (ObjectError error : validation.getValidationErrors().getAllErrors()) {
          keys.add(normalize(error instanceof FieldError f ? f.getField() : error.getCode()));
        }
      }
    }
    keys.removeIf(key -> !RULES.containsKey(key));
    if (keys.isEmpty()) {
      return null;
    }
    var description = new StringBuilder("Invalid configuration:");
    keys.forEach(key -> description.append("\n  - ").append(RULES.get(key)));
    return new FailureAnalysis(
        description.toString(),
        "Set the environment variables above (see .env.example) and restart.",
        cause);
  }

  private static String normalize(@Nullable String key) {
    if (key == null) {
      return "";
    }
    String k = key.startsWith(PREFIX) ? key.substring(PREFIX.length()) : key;
    return k.equals(DISTINCT)
        ? k
        : k.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
  }
}
