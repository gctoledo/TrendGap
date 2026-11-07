package io.github.gctoledo.trendgap.platform.status.adapter.out.mlservice;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gctoledo.trendgap.platform.mlclient.ApiClient;
import io.github.gctoledo.trendgap.platform.mlclient.api.HealthApi;
import io.github.gctoledo.trendgap.platform.status.domain.HealthStatus;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@Testcontainers
class MlServiceContractIT {

  private static final Path CONTRACT =
      Path.of(System.getProperty("user.dir"), "..", "contracts", "ml-service.openapi.yaml")
          .toAbsolutePath()
          .normalize();

  // 5.16.0 does not exist on Docker Hub; 5.15.x crashes on mock (cluster isPrimary TypeError).
  @Container
  static final GenericContainer<?> PRISM =
      new GenericContainer<>(DockerImageName.parse("stoplight/prism:5.14.3"))
          .withCopyFileToContainer(
              MountableFile.forHostPath(CONTRACT), "/contracts/ml-service.openapi.yaml")
          .withCommand("mock", "-h", "0.0.0.0", "-p", "4010", "/contracts/ml-service.openapi.yaml")
          .withExposedPorts(4010)
          .waitingFor(Wait.forHttp("/health").forStatusCode(200));

  @Test
  void probeIsUpAgainstPrismMockOfTheContract() {
    String baseUrl = "http://" + PRISM.getHost() + ":" + PRISM.getMappedPort(4010);
    ApiClient apiClient = new ApiClient(RestClient.builder().baseUrl(baseUrl).build());
    apiClient.setBasePath(baseUrl);
    var probe = new MlServiceProbe(new HealthApi(apiClient));

    assertThat(probe.probe()).isEqualTo(HealthStatus.UP);
  }
}
