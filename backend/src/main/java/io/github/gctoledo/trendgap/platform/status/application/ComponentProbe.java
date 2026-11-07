package io.github.gctoledo.trendgap.platform.status.application;

import io.github.gctoledo.trendgap.platform.status.domain.ComponentName;
import io.github.gctoledo.trendgap.platform.status.domain.HealthStatus;

/** Outbound port: checks one component. */
public interface ComponentProbe {

  ComponentName name();

  HealthStatus probe();
}
