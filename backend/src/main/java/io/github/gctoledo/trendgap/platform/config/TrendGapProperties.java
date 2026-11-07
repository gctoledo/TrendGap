package io.github.gctoledo.trendgap.platform.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Fail-fast configuration validated at startup (data-model.md section 4). */
@ConfigurationProperties("trendgap")
@Validated
public record TrendGapProperties(
    @Valid @DefaultValue Db db,
    @Valid @DefaultValue Ml ml,
    @Valid @DefaultValue Http http,
    @Valid @DefaultValue Management management,
    @Valid @DefaultValue Otel otel) {

  /** Management port must differ from the HTTP port. */
  @AssertTrue
  public boolean isManagementPortDistinct() {
    return management.port() != http.port();
  }

  /** Database connection settings. */
  public record Db(
      @NotBlank @Pattern(regexp = "^jdbc:postgresql://.+") @Nullable String url,
      @NotBlank @Nullable String username,
      @NotBlank @Nullable String password) {

    @Override
    public String toString() {
      return "Db[url=" + url + ", username=" + username + ", password=***]";
    }
  }

  /** ML service client settings. */
  public record Ml(
      @NotBlank @Pattern(regexp = "^https?://\\S+$") @Nullable String baseUrl,
      @DurationMin(millis = 100) @DurationMax(seconds = 5) @DefaultValue("2s") Duration timeout) {}

  /** HTTP server port. */
  public record Http(@Min(1) @Max(65535) @DefaultValue("8080") int port) {}

  /** Management server port. */
  public record Management(@Min(1) @Max(65535) @DefaultValue("8081") int port) {}

  /** OTLP export endpoint; blank is treated as absent. */
  public record Otel(
      @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9+.-]*://\\S+$") @Nullable String endpoint) {

    public Otel {
      if (endpoint != null && endpoint.isBlank()) {
        endpoint = null;
      }
    }
  }
}
