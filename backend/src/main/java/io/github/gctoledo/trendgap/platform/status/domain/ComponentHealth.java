package io.github.gctoledo.trendgap.platform.status.domain;

import java.time.Instant;

public record ComponentHealth(ComponentName name, HealthStatus status, Instant checkedAt) {}
