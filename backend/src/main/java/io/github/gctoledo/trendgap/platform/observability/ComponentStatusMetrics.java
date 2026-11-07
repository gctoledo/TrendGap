package io.github.gctoledo.trendgap.platform.observability;

import io.github.gctoledo.trendgap.platform.status.domain.ComponentHealth;
import io.github.gctoledo.trendgap.platform.status.domain.HealthStatus;
import io.github.gctoledo.trendgap.platform.status.domain.SystemStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Publishes the last observed status of each component as gauge trendgap.component.status. */
public class ComponentStatusMetrics {

  private static final String METER = "trendgap.component.status";

  private final MeterRegistry registry;
  // Strong references: Micrometer gauges only hold their state weakly.
  private final Map<String, AtomicInteger> values = new ConcurrentHashMap<>();

  public ComponentStatusMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  public void record(SystemStatus status) {
    for (ComponentHealth component : status.components()) {
      String name = component.name().value();
      int value = component.status() == HealthStatus.UP ? 1 : 0;
      values
          .computeIfAbsent(
              name,
              key -> {
                var state = new AtomicInteger();
                Gauge.builder(METER, state, AtomicInteger::get)
                    .tag("component", key)
                    .description("Component status: 1 = UP, 0 = DOWN")
                    .register(registry);
                return state;
              })
          .set(value);
    }
  }
}
