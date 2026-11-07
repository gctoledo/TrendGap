package io.github.gctoledo.trendgap.platform.status;

import io.github.gctoledo.trendgap.platform.config.TrendGapProperties;
import io.github.gctoledo.trendgap.platform.status.adapter.out.database.DatabaseProbe;
import io.github.gctoledo.trendgap.platform.status.application.ComponentProbe;
import io.github.gctoledo.trendgap.platform.status.application.StatusService;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the status service and its probes. */
@Configuration(proxyBeanMethods = false)
public class StatusConfig {

  private static final Duration PROBE_MARGIN = Duration.ofMillis(500);
  private static final Duration DB_TIMEOUT = Duration.ofSeconds(2);

  @Bean
  DatabaseProbe databaseProbe(DataSource dataSource) {
    return new DatabaseProbe(dataSource, DB_TIMEOUT);
  }

  @Bean
  StatusService statusService(List<ComponentProbe> probes, TrendGapProperties properties) {
    return new StatusService(
        probes,
        slowest(properties.ml().timeout(), DB_TIMEOUT).plus(PROBE_MARGIN),
        Clock.systemUTC());
  }

  private static Duration slowest(Duration a, Duration b) {
    return a.compareTo(b) >= 0 ? a : b;
  }
}
