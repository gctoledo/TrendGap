package io.github.gctoledo.trendgap.platform.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TracePropagationIT {

  private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e4e4736";
  private static final String TRACEPARENT = "00-" + TRACE_ID + "-00f067aa0ba902b7-01";

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("pgvector/pgvector:0.8.7-pg18-trixie")
              .asCompatibleSubstituteFor("postgres"));

  private static final AtomicReference<String> RECEIVED_TRACEPARENT = new AtomicReference<>();
  private static final HttpServer ML_STUB = startStub();

  @LocalServerPort private int port;

  private static HttpServer startStub() {
    try {
      var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      server.createContext(
          "/health",
          exchange -> {
            RECEIVED_TRACEPARENT.set(exchange.getRequestHeaders().getFirst("traceparent"));
            byte[] body = "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) {
              out.write(body);
            }
          });
      server.start();
      return server;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @AfterAll
  static void stopStub() {
    ML_STUB.stop(0);
  }

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
    registry.add(
        "trendgap.ml.base-url", () -> "http://localhost:" + ML_STUB.getAddress().getPort());
    registry.add("trendgap.http.port", TracePropagationIT::freePort);
    registry.add("trendgap.management.port", TracePropagationIT::freePort);
  }

  private static JsonNode field(JsonNode json, String dotted) {
    JsonNode flat = json.get(dotted);
    if (flat != null) {
      return flat;
    }
    JsonNode current = json;
    for (String part : dotted.split("\\.")) {
      current = current.get(part);
      if (current == null) {
        return tools.jackson.databind.node.MissingNode.getInstance();
      }
    }
    return current;
  }

  @Test
  void traceparentIsPropagatedToMlAndAppearsInStructuredJsonLogs(CapturedOutput output)
      throws Exception {
    int before = output.getAll().length();

    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/status"))
            .header("traceparent", TRACEPARENT)
            .build();
    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);

    // Logging may be asynchronous: wait briefly for the trace id to show up.
    long deadline = System.currentTimeMillis() + 5000;
    while (!output.getAll().substring(before).contains(TRACE_ID)
        && System.currentTimeMillis() < deadline) {
      Thread.sleep(100);
    }

    String forwarded = RECEIVED_TRACEPARENT.get();
    assertThat(forwarded).as("traceparent received by ML stub").isNotNull();
    assertThat(forwarded).matches("00-" + TRACE_ID + "-[0-9a-f]{16}-[0-9a-f]{2}");

    var mapper = JsonMapper.builder().build();
    List<JsonNode> lines = new ArrayList<>();
    for (String line : output.getAll().substring(before).split("\\R")) {
      if (line.isBlank()) {
        continue;
      }
      JsonNode json = mapper.readTree(line);
      assertThat(json.isObject()).as("log line is a JSON object: %s", line).isTrue();
      for (String key : new String[] {"@timestamp", "log.level", "log.logger", "message"}) {
        assertThat(field(json, key).isMissingNode()).as("%s in %s", key, line).isFalse();
      }
      assertThat(field(json, "service.name").asString()).as(line).isEqualTo("backend");
      lines.add(json);
    }

    assertThat(lines).as("JSON log lines emitted during request").isNotEmpty();
    assertThat(lines)
        .anySatisfy(json -> assertThat(field(json, "trace.id").asString()).isEqualTo(TRACE_ID));
  }
}
