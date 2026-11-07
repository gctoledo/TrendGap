package io.github.gctoledo.trendgap.platform.status.domain;

import java.util.List;

public record SystemStatus(List<ComponentHealth> components) {}
