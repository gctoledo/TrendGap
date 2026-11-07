package io.github.gctoledo.trendgap.platform.status.adapter.out.mlservice;

import io.github.gctoledo.trendgap.platform.mlclient.api.HealthApi;
import io.github.gctoledo.trendgap.platform.mlclient.model.Health;
import io.github.gctoledo.trendgap.platform.status.application.ComponentProbe;
import io.github.gctoledo.trendgap.platform.status.domain.ComponentName;
import io.github.gctoledo.trendgap.platform.status.domain.HealthStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** ML service probe: UP only when GET /health answers 2xx with {"status":"UP"}. */
public class MlServiceProbe implements ComponentProbe {

  private static final Logger LOG = LoggerFactory.getLogger(MlServiceProbe.class);

  private final HealthApi healthApi;

  public MlServiceProbe(HealthApi healthApi) {
    this.healthApi = healthApi;
  }

  @Override
  public ComponentName name() {
    return ComponentName.ML_SERVICE;
  }

  @Override
  public HealthStatus probe() {
    long start = System.nanoTime();
    HealthStatus result = HealthStatus.DOWN;
    try {
      Health health = healthApi.getHealth();
      if (health != null && health.getStatus() == Health.StatusEnum.UP) {
        result = HealthStatus.UP;
      }
    } catch (RuntimeException e) {
      LOG.warn("ML service probe failed: {}", e.getClass().getSimpleName());
    }
    LOG.info(
        "ML service probe result={} durationMs={}",
        result,
        (System.nanoTime() - start) / 1_000_000);
    return result;
  }
}
