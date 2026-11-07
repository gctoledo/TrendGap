package io.github.gctoledo.trendgap.platform.status.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gctoledo.trendgap.platform.status.domain.ComponentHealth;
import io.github.gctoledo.trendgap.platform.status.domain.ComponentName;
import io.github.gctoledo.trendgap.platform.status.domain.HealthStatus;
import io.github.gctoledo.trendgap.platform.status.domain.SystemStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Contract for the implementation (T021): {@code StatusService(List<ComponentProbe>, Duration
 * probeTimeout, Clock)} with {@code SystemStatus check()}. Backend is always UP without a probe;
 * missing database/ml-service probes are DOWN; probes run in parallel with a timeout.
 */
class StatusServiceTest {

  private static final Instant NOW = Instant.parse("2026-01-02T03:04:05Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final Duration TIMEOUT = Duration.ofMillis(500);

  private static ComponentProbe probe(ComponentName name, Supplier<HealthStatus> action) {
    return new ComponentProbe() {
      @Override
      public ComponentName name() {
        return name;
      }

      @Override
      public HealthStatus probe() {
        return action.get();
      }
    };
  }

  private static ComponentProbe fixed(ComponentName name, HealthStatus status) {
    return probe(name, () -> status);
  }

  private static ComponentProbe sleeping(ComponentName name, long millis, HealthStatus status) {
    return probe(
        name,
        () -> {
          try {
            Thread.sleep(millis);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return status;
        });
  }

  private static HealthStatus statusOf(SystemStatus s, ComponentName name) {
    return s.components().stream().filter(c -> c.name() == name).findFirst().orElseThrow().status();
  }

  @Test
  void returnsFixedOrderEvenWhenProbesAreProvidedOutOfOrder() {
    var service =
        new StatusService(
            List.of(
                fixed(ComponentName.ML_SERVICE, HealthStatus.UP),
                fixed(ComponentName.DATABASE, HealthStatus.UP)),
            TIMEOUT,
            CLOCK);

    var result = service.check();

    assertThat(result.components())
        .extracting(ComponentHealth::name)
        .containsExactly(ComponentName.BACKEND, ComponentName.DATABASE, ComponentName.ML_SERVICE);
    assertThat(result.components())
        .extracting(c -> c.name().value())
        .containsExactly("backend", "database", "ml-service");
  }

  @Test
  void backendIsAlwaysUpWithoutProbe() {
    var service = new StatusService(List.of(), TIMEOUT, CLOCK);

    assertThat(statusOf(service.check(), ComponentName.BACKEND)).isEqualTo(HealthStatus.UP);
  }

  @Test
  void missingProbesForDatabaseAndMlServiceAreDown() {
    var service = new StatusService(List.of(), TIMEOUT, CLOCK);

    var result = service.check();

    assertThat(statusOf(result, ComponentName.DATABASE)).isEqualTo(HealthStatus.DOWN);
    assertThat(statusOf(result, ComponentName.ML_SERVICE)).isEqualTo(HealthStatus.DOWN);
  }

  @Test
  void runsProbesInParallel() {
    var service =
        new StatusService(
            List.of(
                sleeping(ComponentName.DATABASE, 300, HealthStatus.UP),
                sleeping(ComponentName.ML_SERVICE, 300, HealthStatus.UP)),
            Duration.ofSeconds(2),
            CLOCK);

    long start = System.nanoTime();
    var result = service.check();
    long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

    assertThat(elapsedMs).isLessThan(550);
    assertThat(statusOf(result, ComponentName.DATABASE)).isEqualTo(HealthStatus.UP);
    assertThat(statusOf(result, ComponentName.ML_SERVICE)).isEqualTo(HealthStatus.UP);
  }

  @Test
  void probeExceptionYieldsDownWithoutAffectingOthers() {
    var service =
        new StatusService(
            List.of(
                probe(
                    ComponentName.DATABASE,
                    () -> {
                      throw new IllegalStateException("boom");
                    }),
                fixed(ComponentName.ML_SERVICE, HealthStatus.UP)),
            TIMEOUT,
            CLOCK);

    var result = service.check();

    assertThat(statusOf(result, ComponentName.DATABASE)).isEqualTo(HealthStatus.DOWN);
    assertThat(statusOf(result, ComponentName.ML_SERVICE)).isEqualTo(HealthStatus.UP);
    assertThat(statusOf(result, ComponentName.BACKEND)).isEqualTo(HealthStatus.UP);
  }

  @Test
  void probeTimeoutYieldsDownWithoutAffectingOthersAndWithoutWaiting() {
    var service =
        new StatusService(
            List.of(
                sleeping(ComponentName.DATABASE, 3000, HealthStatus.UP),
                fixed(ComponentName.ML_SERVICE, HealthStatus.UP)),
            Duration.ofMillis(200),
            CLOCK);

    long start = System.nanoTime();
    var result = service.check();
    long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

    assertThat(elapsedMs).isLessThan(1500);
    assertThat(statusOf(result, ComponentName.DATABASE)).isEqualTo(HealthStatus.DOWN);
    assertThat(statusOf(result, ComponentName.ML_SERVICE)).isEqualTo(HealthStatus.UP);
  }

  @Test
  void resultNeverContainsExceptionMessages() {
    var service =
        new StatusService(
            List.of(
                probe(
                    ComponentName.DATABASE,
                    () -> {
                      throw new IllegalStateException("secret-jdbc-password-leak");
                    }),
                fixed(ComponentName.ML_SERVICE, HealthStatus.UP)),
            TIMEOUT,
            CLOCK);

    var result = service.check();

    assertThat(result.toString()).doesNotContain("secret-jdbc-password-leak");
    assertThat(result.toString()).doesNotContain("IllegalStateException");
  }

  @Test
  void checkedAtIsClockInstantInUtcForEveryComponent() {
    var service =
        new StatusService(
            List.of(
                fixed(ComponentName.DATABASE, HealthStatus.UP),
                probe(
                    ComponentName.ML_SERVICE,
                    () -> {
                      throw new IllegalStateException("x");
                    })),
            TIMEOUT,
            CLOCK);

    var result = service.check();

    assertThat(result.components()).hasSize(3);
    assertThat(result.components()).extracting(ComponentHealth::checkedAt).containsOnly(NOW);
  }
}
