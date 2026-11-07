package io.github.gctoledo.trendgap.platform.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class FlywayMigrationIT {

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("pgvector/pgvector:0.8.7-pg18-trixie")
              .asCompatibleSubstituteFor("postgres"));

  private static Flyway flyway() {
    return Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:db/migration")
        .load();
  }

  @Test
  void migratesOnPostgres18AndEnablesVectorIdempotently() throws SQLException {
    flyway().migrate();
    flyway().migrate();

    try (Connection c =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var rs =
            c.createStatement()
                .executeQuery("select current_setting('server_version_num')::int / 10000")) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getInt(1)).isEqualTo(18);
    }
    try (Connection c =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var rs =
            c.createStatement().executeQuery("select 1 from pg_extension where extname='vector'")) {
      assertThat(rs.next()).isTrue();
    }
  }
}
