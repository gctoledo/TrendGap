package io.github.gctoledo.trendgap.platform.status.adapter.in.web;

import io.github.gctoledo.trendgap.platform.status.application.StatusService;
import io.github.gctoledo.trendgap.platform.status.domain.ComponentHealth;
import io.github.gctoledo.trendgap.platform.status.domain.HealthStatus;
import io.github.gctoledo.trendgap.platform.status.domain.SystemStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public status endpoint; always answers 200 when the backend is up. */
@RestController
public class StatusController {

  private final StatusService statusService;

  public StatusController(StatusService statusService) {
    this.statusService = statusService;
  }

  @GetMapping("/api/status")
  public StatusResponse status() {
    SystemStatus status = statusService.check();
    return new StatusResponse(status.components().stream().map(ComponentResponse::from).toList());
  }

  /** Response body of {@code GET /api/status}. */
  public record StatusResponse(List<ComponentResponse> components) {}

  /** One component entry: stable name, UP/DOWN and probe completion time. */
  public record ComponentResponse(String name, HealthStatus status, Instant checkedAt) {
    static ComponentResponse from(ComponentHealth health) {
      return new ComponentResponse(health.name().value(), health.status(), health.checkedAt());
    }
  }
}
