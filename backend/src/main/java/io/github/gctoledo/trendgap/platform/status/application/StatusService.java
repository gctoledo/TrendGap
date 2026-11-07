package io.github.gctoledo.trendgap.platform.status.application;

import io.github.gctoledo.trendgap.platform.status.domain.ComponentHealth;
import io.github.gctoledo.trendgap.platform.status.domain.ComponentName;
import io.github.gctoledo.trendgap.platform.status.domain.HealthStatus;
import io.github.gctoledo.trendgap.platform.status.domain.SystemStatus;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Runs the component probes in parallel; a failing or slow probe only marks itself DOWN. */
public class StatusService {

  private static final Logger LOG = LoggerFactory.getLogger(StatusService.class);

  private final Map<ComponentName, ComponentProbe> probes = new EnumMap<>(ComponentName.class);
  private final Duration probeTimeout;
  private final Clock clock;

  public StatusService(List<ComponentProbe> probes, Duration probeTimeout, Clock clock) {
    for (ComponentProbe probe : probes) {
      this.probes.put(probe.name(), probe);
    }
    this.probeTimeout = probeTimeout;
    this.clock = clock;
  }

  public SystemStatus check() {
    var results = new ArrayList<ComponentHealth>();
    results.add(new ComponentHealth(ComponentName.BACKEND, HealthStatus.UP, clock.instant()));
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var futures = new EnumMap<ComponentName, Future<ComponentHealth>>(ComponentName.class);
      for (ComponentName name : ComponentName.values()) {
        ComponentProbe probe = probes.get(name);
        if (name != ComponentName.BACKEND && probe != null) {
          futures.put(name, executor.submit(() -> run(probe)));
        }
      }
      long deadline = System.nanoTime() + probeTimeout.toNanos();
      for (ComponentName name : ComponentName.values()) {
        if (name == ComponentName.BACKEND) {
          continue;
        }
        Future<ComponentHealth> future = futures.get(name);
        results.add(future == null ? down(name) : await(name, future, deadline));
      }
    }
    return new SystemStatus(List.copyOf(results));
  }

  private ComponentHealth run(ComponentProbe probe) {
    return new ComponentHealth(probe.name(), probe.probe(), clock.instant());
  }

  private ComponentHealth await(ComponentName name, Future<ComponentHealth> future, long deadline) {
    try {
      long remaining = Math.max(0, deadline - System.nanoTime());
      return future.get(remaining, TimeUnit.NANOSECONDS);
    } catch (TimeoutException e) {
      future.cancel(true);
      LOG.warn("Probe {} timed out", name.value());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warn("Probe {} interrupted", name.value());
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      LOG.warn(
          "Probe {} failed: {}",
          name.value(),
          cause == null ? "unknown" : cause.getClass().getSimpleName());
    }
    return down(name);
  }

  private ComponentHealth down(ComponentName name) {
    return new ComponentHealth(name, HealthStatus.DOWN, clock.instant());
  }
}
