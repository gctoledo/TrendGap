package io.github.gctoledo.trendgap.platform.status.adapter.out.database;

import io.github.gctoledo.trendgap.platform.status.application.ComponentProbe;
import io.github.gctoledo.trendgap.platform.status.domain.ComponentName;
import io.github.gctoledo.trendgap.platform.status.domain.HealthStatus;
import java.sql.SQLException;
import java.time.Duration;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Database probe: obtains a connection and validates it within the timeout. */
public class DatabaseProbe implements ComponentProbe {

  private static final Logger LOG = LoggerFactory.getLogger(DatabaseProbe.class);

  private final DataSource dataSource;
  private final int timeoutSeconds;

  public DatabaseProbe(DataSource dataSource, Duration timeout) {
    this.dataSource = dataSource;
    this.timeoutSeconds = (int) Math.max(1, (timeout.toMillis() + 999) / 1000);
  }

  @Override
  public ComponentName name() {
    return ComponentName.DATABASE;
  }

  @Override
  public HealthStatus probe() {
    try (var connection = dataSource.getConnection()) {
      return connection.isValid(timeoutSeconds) ? HealthStatus.UP : HealthStatus.DOWN;
    } catch (SQLException e) {
      LOG.warn("Database probe failed (SQLState {})", e.getSQLState());
      return HealthStatus.DOWN;
    }
  }
}
