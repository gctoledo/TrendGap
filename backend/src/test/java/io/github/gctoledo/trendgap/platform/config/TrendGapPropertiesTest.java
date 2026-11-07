package io.github.gctoledo.trendgap.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Contract for the implementation (T016):
 *
 * <ul>
 *   <li>{@code @ConfigurationProperties("trendgap") @Validated record TrendGapProperties(Db db, Ml
 *       ml, Http http, Management management, Otel otel)}. The nested records are always created
 *       (use {@code @DefaultValue} on the components) so a missing leaf yields a validation error
 *       on that leaf, never a null parent.
 *   <li>{@code Db(String url, String username, String password)}: url starts with {@code
 *       jdbc:postgresql://}; username and password not blank.
 *   <li>{@code Ml(String baseUrl, @DefaultValue("2s") Duration timeout)}: baseUrl is an absolute
 *       http/https URL; timeout between 100ms and 5s.
 *   <li>{@code Http(@DefaultValue("8080") int port)} bound at {@code trendgap.http.port} (env
 *       TRENDGAP_HTTP_PORT), {@code Management(@DefaultValue("8081") int port)} at {@code
 *       trendgap.management.port} (env TRENDGAP_MANAGEMENT_PORT); both 1-65535 and the management
 *       port must differ from the http port (application.yaml maps them to server.port and
 *       management.server.port).
 *   <li>{@code Otel(String endpoint)} bound at {@code trendgap.otel.endpoint} (env
 *       OTEL_EXPORTER_OTLP_ENDPOINT): absolute URL; blank or missing is normalized to {@code null}.
 *   <li>{@code ConfigFailureAnalyzer extends AbstractFailureAnalyzer<Throwable-subtype>} (public
 *       no-arg constructor): returns a FailureAnalysis whose description names the environment
 *       variable and the rule, never the offending value; returns null for unrelated failures.
 * </ul>
 */
class TrendGapPropertiesTest {

  private static final String SECRET = "s3cr3t-pa55-value";

  @Configuration
  @EnableConfigurationProperties(TrendGapProperties.class)
  static class TestConfig {}

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

  private static List<String> valid() {
    return new ArrayList<>(
        List.of(
            "trendgap.db.url=jdbc:postgresql://localhost:5432/trendgap",
            "trendgap.db.username=app",
            "trendgap.db.password=" + SECRET,
            "trendgap.ml.base-url=http://ml:8000"));
  }

  private static String[] without(String key) {
    List<String> props = valid();
    props.removeIf(p -> p.startsWith(key + "="));
    return props.toArray(String[]::new);
  }

  private static String[] with(String key, String value) {
    List<String> props = valid();
    props.removeIf(p -> p.startsWith(key + "="));
    props.add(key + "=" + value);
    return props.toArray(String[]::new);
  }

  private Throwable failureOf(String... props) {
    var holder = new @Nullable Throwable[1];
    runner.withPropertyValues(props).run(ctx -> holder[0] = ctx.getStartupFailure());
    Throwable failure = holder[0];
    assertThat(failure).as("startup failure").isNotNull();
    return Objects.requireNonNull(failure);
  }

  private String descriptionOf(String... props) {
    FailureAnalysis analysis = new ConfigFailureAnalyzer().analyze(failureOf(props));
    assertThat(analysis).as("analysis").isNotNull();
    return Objects.requireNonNull(analysis).getDescription();
  }

  private void assertRejected(String[] props, String envVar, String rule, String leakedValue) {
    String description = descriptionOf(props);
    assertThat(description).contains(envVar).containsIgnoringCase(rule);
    assertThat(description).doesNotContain(leakedValue).doesNotContain(SECRET);
  }

  @Test
  void bindsAllValuesWhenConfigurationIsValid() {
    runner
        .withPropertyValues(valid().toArray(String[]::new))
        .run(ctx -> {
          assertThat(ctx).hasNotFailed();
          TrendGapProperties p = ctx.getBean(TrendGapProperties.class);
          assertThat(p.db().url()).isEqualTo("jdbc:postgresql://localhost:5432/trendgap");
          assertThat(p.db().username()).isEqualTo("app");
          assertThat(p.db().password()).isEqualTo(SECRET);
          assertThat(p.ml().baseUrl()).isEqualTo("http://ml:8000");
        });
  }

  @Test
  void appliesDefaultsForTimeoutAndPortsAndNoOtel() {
    runner
        .withPropertyValues(valid().toArray(String[]::new))
        .run(ctx -> {
          TrendGapProperties p = ctx.getBean(TrendGapProperties.class);
          assertThat(p.ml().timeout()).isEqualTo(Duration.ofSeconds(2));
          assertThat(p.http().port()).isEqualTo(8080);
          assertThat(p.management().port()).isEqualTo(8081);
          assertThat(p.otel().endpoint()).isNull();
        });
  }

  // db.url

  @Test
  void rejectsMissingDbUrlNamingVariable() {
    String d = descriptionOf(without("trendgap.db.url"));
    assertThat(d).contains("TRENDGAP_DB_URL").doesNotContain(SECRET);
  }

  @Test
  void rejectsDbUrlWithoutPostgresPrefixWithoutLeakingValue() {
    assertRejected(
        with("trendgap.db.url", "mysql://user:hunter2@host/db"),
        "TRENDGAP_DB_URL",
        "jdbc:postgresql://",
        "hunter2");
  }

  // db.username / db.password

  @Test
  void rejectsMissingDbUsername() {
    assertThat(descriptionOf(without("trendgap.db.username"))).contains("TRENDGAP_DB_USERNAME");
  }

  @Test
  void rejectsBlankDbUsername() {
    assertThat(descriptionOf(with("trendgap.db.username", "  "))).contains("TRENDGAP_DB_USERNAME");
  }

  @Test
  void rejectsMissingDbPasswordWithoutLeakingOtherSecrets() {
    String d = descriptionOf(without("trendgap.db.password"));
    assertThat(d).contains("TRENDGAP_DB_PASSWORD").doesNotContain(SECRET);
  }

  @Test
  void rejectsBlankDbPassword() {
    assertThat(descriptionOf(with("trendgap.db.password", ""))).contains("TRENDGAP_DB_PASSWORD");
  }

  // ml.base-url

  @Test
  void rejectsMissingMlBaseUrl() {
    assertThat(descriptionOf(without("trendgap.ml.base-url"))).contains("TRENDGAP_ML_BASE_URL");
  }

  @ParameterizedTest
  @ValueSource(strings = {"ftp://leak-host/x", "not a url", "/relative/path", "ml:8000"})
  void rejectsMlBaseUrlThatIsNotAbsoluteHttpUrl(String bad) {
    assertRejected(with("trendgap.ml.base-url", bad), "TRENDGAP_ML_BASE_URL", "http", bad);
  }

  @Test
  void acceptsHttpsMlBaseUrl() {
    runner
        .withPropertyValues(with("trendgap.ml.base-url", "https://ml.example.com"))
        .run(ctx -> assertThat(ctx).hasNotFailed());
  }

  // ml.timeout

  @ParameterizedTest
  @ValueSource(strings = {"100ms", "2s", "5s"})
  void acceptsMlTimeoutWithinLimits(String value) {
    runner
        .withPropertyValues(with("trendgap.ml.timeout", value))
        .run(ctx -> assertThat(ctx).hasNotFailed());
  }

  @ParameterizedTest
  @ValueSource(strings = {"99ms", "0s", "5001ms", "6s", "1m"})
  void rejectsMlTimeoutOutsideLimits(String bad) {
    assertRejected(with("trendgap.ml.timeout", bad), "TRENDGAP_ML_TIMEOUT", "100ms", bad);
  }

  @Test
  void rejectsUnparseableMlTimeout() {
    assertRejected(with("trendgap.ml.timeout", "banana"), "TRENDGAP_ML_TIMEOUT", "5s", "banana");
  }

  // ports

  @Test
  void acceptsCustomDistinctPorts() {
    runner
        .withPropertyValues(with("trendgap.http.port", "9000"))
        .withPropertyValues("trendgap.management.port=9001")
        .run(ctx -> {
          TrendGapProperties p = ctx.getBean(TrendGapProperties.class);
          assertThat(p.http().port()).isEqualTo(9000);
          assertThat(p.management().port()).isEqualTo(9001);
        });
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "65536", "-1", "abc"})
  void rejectsInvalidHttpPort(String bad) {
    assertRejected(with("trendgap.http.port", bad), "TRENDGAP_HTTP_PORT", "65535", bad);
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "65536", "-1", "abc"})
  void rejectsInvalidManagementPort(String bad) {
    assertRejected(with("trendgap.management.port", bad), "TRENDGAP_MANAGEMENT_PORT", "65535", bad);
  }

  @Test
  void rejectsManagementPortEqualToHttpPort() {
    List<String> props = new ArrayList<>(List.of(with("trendgap.http.port", "9000")));
    props.add("trendgap.management.port=9000");
    String d = descriptionOf(props.toArray(String[]::new));
    assertThat(d).contains("TRENDGAP_MANAGEMENT_PORT").contains("TRENDGAP_HTTP_PORT");
  }

  @Test
  void rejectsManagementPortEqualToDefaultHttpPort() {
    List<String> props = valid();
    props.add("trendgap.management.port=8080");
    assertThat(descriptionOf(props.toArray(String[]::new))).contains("TRENDGAP_MANAGEMENT_PORT");
  }

  // otel

  @Test
  void acceptsAbsoluteOtelEndpoint() {
    runner
        .withPropertyValues(with("trendgap.otel.endpoint", "http://collector:4318"))
        .run(ctx ->
            assertThat(ctx.getBean(TrendGapProperties.class).otel().endpoint())
                .isEqualTo("http://collector:4318"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void treatsBlankOtelEndpointAsAbsent(String blank) {
    runner
        .withPropertyValues(with("trendgap.otel.endpoint", blank))
        .run(ctx -> {
          assertThat(ctx).hasNotFailed();
          assertThat(ctx.getBean(TrendGapProperties.class).otel().endpoint()).isNull();
        });
  }

  @ParameterizedTest
  @ValueSource(strings = {"collector:4318", "/just/a/path", "not a url"})
  void rejectsRelativeOtelEndpoint(String bad) {
    assertRejected(
        with("trendgap.otel.endpoint", bad), "OTEL_EXPORTER_OTLP_ENDPOINT", "absolute", bad);
  }

  // analyzer

  @Test
  void analyzerIgnoresUnrelatedFailures() {
    assertThat(new ConfigFailureAnalyzer().analyze(new IllegalStateException("boom"))).isNull();
  }
}
