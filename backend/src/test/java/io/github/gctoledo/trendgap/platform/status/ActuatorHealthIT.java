package io.github.gctoledo.trendgap.platform.status;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ActuatorHealthIT {

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("pgvector/pgvector:0.8.7-pg18-trixie")
              .asCompatibleSubstituteFor("postgres"));

  @LocalManagementPort private int managementPort;

  private static int freePort() {
    try (var socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("trendgap.db.url", POSTGRES::getJdbcUrl);
    registry.add("trendgap.db.username", POSTGRES::getUsername);
    registry.add("trendgap.db.password", POSTGRES::getPassword);
    registry.add("trendgap.ml.base-url", () -> "http://localhost:9");
    registry.add("trendgap.http.port", ActuatorHealthIT::freePort);
    registry.add("trendgap.management.port", ActuatorHealthIT::freePort);
  }

  private HttpResponse<String> get(String path) throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://localhost:" + managementPort + path));
    return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  @Test
  @Order(1)
  void healthLivenessAndReadinessExposeOnlyStatusUp() throws Exception {
    HttpResponse<String> root = get("/actuator/health");
    assertThat(root.statusCode()).isEqualTo(200);
    // Boot lists the probe group names at the root; no components or details may appear.
    assertThat(root.body())
        .isEqualToIgnoringWhitespace("{\"groups\":[\"liveness\",\"readiness\"],\"status\":\"UP\"}");
    for (String path : new String[] {"/actuator/health/liveness", "/actuator/health/readiness"}) {
      HttpResponse<String> response = get(path);
      assertThat(response.statusCode()).as(path).isEqualTo(200);
      assertThat(response.body()).as(path).isEqualToIgnoringWhitespace("{\"status\":\"UP\"}");
    }
  }

  @Test
  @Order(2)
  void readinessIsDownWithoutDatabaseWhileLivenessStaysUp() throws Exception {
    POSTGRES.stop();

    HttpResponse<String> readiness = get("/actuator/health/readiness");
    assertThat(readiness.statusCode()).isEqualTo(503);
    assertThat(readiness.body()).isEqualToIgnoringWhitespace("{\"status\":\"DOWN\"}");
    assertThat(get("/actuator/health/liveness").body())
        .isEqualToIgnoringWhitespace("{\"status\":\"UP\"}");
  }
}
