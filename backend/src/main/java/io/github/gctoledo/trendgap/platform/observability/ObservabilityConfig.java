package io.github.gctoledo.trendgap.platform.observability;

import io.github.gctoledo.trendgap.platform.status.domain.SystemStatus;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.Consumer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the component status gauge to every /api/status evaluation. */
@Configuration(proxyBeanMethods = false)
public class ObservabilityConfig {

  @Bean
  ComponentStatusMetrics componentStatusMetrics(MeterRegistry registry) {
    return new ComponentStatusMetrics(registry);
  }

  @Bean
  Consumer<SystemStatus> componentStatusListener(ComponentStatusMetrics metrics) {
    return metrics::record;
  }
}
