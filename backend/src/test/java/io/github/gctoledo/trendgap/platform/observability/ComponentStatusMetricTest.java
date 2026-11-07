package io.github.gctoledo.trendgap.platform.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import io.github.gctoledo.trendgap.platform.status.application.StatusService;
import io.github.gctoledo.trendgap.platform.status.domain.ComponentHealth;
import io.github.gctoledo.trendgap.platform.status.domain.ComponentName;
import io.github.gctoledo.trendgap.platform.status.domain.HealthStatus;
import io.github.gctoledo.trendgap.platform.status.domain.SystemStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class ComponentStatusMetricTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private static SystemStatus status(HealthStatus backend, HealthStatus database, HealthStatus ml) {
    return new SystemStatus(
        List.of(
            new ComponentHealth(ComponentName.BACKEND, backend, NOW),
            new ComponentHealth(ComponentName.DATABASE, database, NOW),
            new ComponentHealth(ComponentName.ML_SERVICE, ml, NOW)));
  }

  private static double gauge(MeterRegistry registry, ComponentName component) {
    return registry
        .get("trendgap.component.status")
        .tag("component", component.value())
        .gauge()
        .value();
  }

  @Test
  void gaugeIsOneForUpAndZeroForDownPerComponent() {
    var registry = new SimpleMeterRegistry();
    var metrics = new ComponentStatusMetrics(registry);

    metrics.record(status(HealthStatus.UP, HealthStatus.DOWN, HealthStatus.UP));

    assertThat(gauge(registry, ComponentName.BACKEND)).isEqualTo(1.0);
    assertThat(gauge(registry, ComponentName.DATABASE)).isEqualTo(0.0);
    assertThat(gauge(registry, ComponentName.ML_SERVICE)).isEqualTo(1.0);
  }

  @Test
  void gaugeUpdatesOnLaterRecord() {
    var registry = new SimpleMeterRegistry();
    var metrics = new ComponentStatusMetrics(registry);
    metrics.record(status(HealthStatus.UP, HealthStatus.DOWN, HealthStatus.DOWN));

    metrics.record(status(HealthStatus.UP, HealthStatus.UP, HealthStatus.DOWN));

    assertThat(gauge(registry, ComponentName.DATABASE)).isEqualTo(1.0);
    assertThat(gauge(registry, ComponentName.ML_SERVICE)).isEqualTo(0.0);
  }

  @Test
  void prometheusScrapeExposesComponentStatusGauge() {
    var registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    var metrics = new ComponentStatusMetrics(registry);

    metrics.record(status(HealthStatus.UP, HealthStatus.DOWN, HealthStatus.UP));

    assertThat(registry.scrape())
        .containsPattern("trendgap_component_status\\{[^}]*component=\"database\"[^}]*\\} 0\\.0")
        .containsPattern("trendgap_component_status\\{[^}]*component=\"backend\"[^}]*\\} 1\\.0");
  }

  /** Runs without Docker: DataSource and StatusService are mocked, DB autoconfig is excluded. */
  @SpringBootTest(
      webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
      properties = {
        "spring.autoconfigure.exclude="
            + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
            + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
            + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
            + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
        "trendgap.db.url=jdbc:postgresql://localhost:5432/unused",
        "trendgap.db.username=unused",
        "trendgap.db.password=unused",
        "trendgap.ml.base-url=http://localhost:9"
      })
  @Nested
  class PrometheusEndpointTest {

    @MockitoBean DataSource dataSource;
    @MockitoBean StatusService statusService;

    @LocalServerPort int port;

    private static final int MANAGEMENT_PORT = freePort();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
      registry.add("trendgap.http.port", PrometheusEndpointTest::freePort);
      registry.add("trendgap.management.port", () -> MANAGEMENT_PORT);
    }

    private static int freePort() {
      try (var socket = new ServerSocket(0)) {
        return socket.getLocalPort();
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }

    @Test
    void prometheusEndpointExposesRequestCountAndDurationAfterRequest() throws Exception {
      given(statusService.check())
          .willReturn(status(HealthStatus.UP, HealthStatus.UP, HealthStatus.UP));
      var client = HttpClient.newHttpClient();

      client.send(
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/status")).build(),
          HttpResponse.BodyHandlers.ofString());
      var scrape =
          client.send(
              HttpRequest.newBuilder(
                      URI.create("http://localhost:" + MANAGEMENT_PORT + "/actuator/prometheus"))
                  .build(),
              HttpResponse.BodyHandlers.ofString());

      assertThat(scrape.statusCode()).isEqualTo(200);
      assertThat(scrape.body())
          .contains("http_server_requests_seconds_count")
          .contains("http_server_requests_seconds_sum");
    }
  }
}
