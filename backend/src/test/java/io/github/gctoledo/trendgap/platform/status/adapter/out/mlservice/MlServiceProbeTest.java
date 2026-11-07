package io.github.gctoledo.trendgap.platform.status.adapter.out.mlservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.sun.net.httpserver.HttpServer;
import io.github.gctoledo.trendgap.platform.mlclient.ApiClient;
import io.github.gctoledo.trendgap.platform.mlclient.api.HealthApi;
import io.github.gctoledo.trendgap.platform.status.domain.ComponentName;
import io.github.gctoledo.trendgap.platform.status.domain.HealthStatus;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MlServiceProbeTest {

  private static final String HEALTH_URL = "http://ml-service:8000/health";

  private MockRestServiceServer server;

  private MlServiceProbe mockedProbe;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://ml-service:8000");
    server = MockRestServiceServer.bindTo(builder).build();
    mockedProbe = new MlServiceProbe(new HealthApi(new ApiClient(builder.build())));
  }

  private MlServiceProbe probeWithMock() {
    return mockedProbe;
  }

  @Test
  void nameIsMlService() {
    assertThat(probeWithMock().name()).isEqualTo(ComponentName.ML_SERVICE);
  }

  @Test
  void reportsUpWhenHealthReturnsStatusUp() {
    MlServiceProbe probe = probeWithMock();
    server
        .expect(requestTo(HEALTH_URL))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("{\"status\":\"UP\"}", MediaType.APPLICATION_JSON));

    assertThat(probe.probe()).isEqualTo(HealthStatus.UP);
    server.verify();
  }

  @Test
  void reportsDownWhenStatusIsDown() {
    MlServiceProbe probe = probeWithMock();
    server
        .expect(requestTo(HEALTH_URL))
        .andRespond(withSuccess("{\"status\":\"DOWN\"}", MediaType.APPLICATION_JSON));

    assertThat(probe.probe()).isEqualTo(HealthStatus.DOWN);
  }

  @Test
  void reportsDownWhenJsonHasNoStatus() {
    MlServiceProbe probe = probeWithMock();
    server
        .expect(requestTo(HEALTH_URL))
        .andRespond(withSuccess("{\"foo\":\"bar\"}", MediaType.APPLICATION_JSON));

    assertThat(probe.probe()).isEqualTo(HealthStatus.DOWN);
  }

  @Test
  void reportsDownWhenBodyIsNotJson() {
    MlServiceProbe probe = probeWithMock();
    server
        .expect(requestTo(HEALTH_URL))
        .andRespond(withSuccess("not json at all", MediaType.TEXT_PLAIN));

    assertThat(probe.probe()).isEqualTo(HealthStatus.DOWN);
  }

  @Test
  void reportsDownOnServerError() {
    MlServiceProbe probe = probeWithMock();
    server.expect(requestTo(HEALTH_URL)).andRespond(withServerError());

    assertThat(probe.probe()).isEqualTo(HealthStatus.DOWN);
  }

  @Test
  void reportsDownOnNetworkError() {
    MlServiceProbe probe = probeWithMock();
    server
        .expect(requestTo(HEALTH_URL))
        .andRespond(withException(new IOException("connection refused")));

    assertThat(probe.probe()).isEqualTo(HealthStatus.DOWN);
  }

  @Test
  void reportsDownWhenReadTimeoutIsExceeded() throws IOException {
    HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    http.createContext(
        "/health",
        exchange -> {
          try {
            Thread.sleep(2000);
            byte[] body = "{\"status\":\"UP\"}".getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
          } catch (InterruptedException | IOException ignored) {
            // client gave up
          } finally {
            exchange.close();
          }
        });
    http.start();
    try {
      JdkClientHttpRequestFactory factory =
          new JdkClientHttpRequestFactory(HttpClient.newHttpClient());
      factory.setReadTimeout(java.time.Duration.ofMillis(200));
      RestClient client =
          RestClient.builder()
              .baseUrl("http://127.0.0.1:" + http.getAddress().getPort())
              .requestFactory(factory)
              .build();
      MlServiceProbe probe = new MlServiceProbe(new HealthApi(new ApiClient(client)));

      assertThat(probe.probe()).isEqualTo(HealthStatus.DOWN);
    } finally {
      http.stop(0);
    }
  }
}
