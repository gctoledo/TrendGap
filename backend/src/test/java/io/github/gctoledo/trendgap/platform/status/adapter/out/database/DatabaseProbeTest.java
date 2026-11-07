package io.github.gctoledo.trendgap.platform.status.adapter.out.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.gctoledo.trendgap.platform.status.domain.ComponentName;
import io.github.gctoledo.trendgap.platform.status.domain.HealthStatus;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DatabaseProbeTest {

  private DataSource dataSource;
  private Connection connection;
  private DatabaseProbe probe;

  @BeforeEach
  void setUp() throws SQLException {
    dataSource = mock(DataSource.class);
    connection = mock(Connection.class);
    when(dataSource.getConnection()).thenReturn(connection);
    probe = new DatabaseProbe(dataSource, Duration.ofSeconds(2));
  }

  @Test
  void nameIsDatabase() {
    assertThat(probe.name()).isEqualTo(ComponentName.DATABASE);
  }

  @Test
  void reportsUpWhenConnectionIsValid() throws SQLException {
    when(connection.isValid(2)).thenReturn(true);

    assertThat(probe.probe()).isEqualTo(HealthStatus.UP);
  }

  @Test
  void reportsDownWhenConnectionIsNotValid() throws SQLException {
    when(connection.isValid(2)).thenReturn(false);

    assertThat(probe.probe()).isEqualTo(HealthStatus.DOWN);
  }

  @Test
  void reportsDownWhenConnectionIsRefused() throws SQLException {
    when(dataSource.getConnection()).thenThrow(new SQLException("refused"));

    assertThat(probe.probe()).isEqualTo(HealthStatus.DOWN);
  }

  @Test
  void reportsDownWhenValidationThrows() throws SQLException {
    when(connection.isValid(2)).thenThrow(new SQLException("boom"));

    assertThat(probe.probe()).isEqualTo(HealthStatus.DOWN);
  }

  @Test
  void closesConnectionWhenValid() throws SQLException {
    when(connection.isValid(2)).thenReturn(true);

    probe.probe();

    verify(connection).close();
  }

  @Test
  void closesConnectionWhenInvalid() throws SQLException {
    when(connection.isValid(2)).thenReturn(false);

    probe.probe();

    verify(connection).close();
  }

  @Test
  void closesConnectionWhenValidationThrows() throws SQLException {
    when(connection.isValid(2)).thenThrow(new SQLException("boom"));

    probe.probe();

    verify(connection).close();
  }

  @Test
  void passesTimeoutInSecondsToIsValid() throws SQLException {
    when(connection.isValid(2)).thenReturn(true);

    probe.probe();

    verify(connection).isValid(2);
  }
}
