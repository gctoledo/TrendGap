package io.github.gctoledo.trendgap.platform.status.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.gctoledo.trendgap.platform.status.application.StatusService;
import io.github.gctoledo.trendgap.platform.status.domain.ComponentHealth;
import io.github.gctoledo.trendgap.platform.status.domain.ComponentName;
import io.github.gctoledo.trendgap.platform.status.domain.HealthStatus;
import io.github.gctoledo.trendgap.platform.status.domain.SystemStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StatusController.class)
class StatusControllerTest {

  private static final Instant CHECKED_AT = Instant.parse("2026-01-02T03:04:05Z");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private StatusService statusService;

  private void stub(HealthStatus database, HealthStatus ml) {
    given(statusService.check())
        .willReturn(
            new SystemStatus(
                List.of(
                    new ComponentHealth(ComponentName.BACKEND, HealthStatus.UP, CHECKED_AT),
                    new ComponentHealth(ComponentName.DATABASE, database, CHECKED_AT),
                    new ComponentHealth(ComponentName.ML_SERVICE, ml, CHECKED_AT))));
  }

  @Test
  void returnsHttp200AllUp() throws Exception {
    stub(HealthStatus.UP, HealthStatus.UP);
    mockMvc.perform(get("/api/status")).andExpect(status().isOk());
  }

  @Test
  void returnsHttp200EvenWhenDatabaseAndMlServiceAreDown() throws Exception {
    stub(HealthStatus.DOWN, HealthStatus.DOWN);
    mockMvc
        .perform(get("/api/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.components[1].status").value("DOWN"))
        .andExpect(jsonPath("$.components[2].status").value("DOWN"));
  }

  @Test
  void respondsWithJsonContentType() throws Exception {
    stub(HealthStatus.UP, HealthStatus.UP);
    mockMvc
        .perform(get("/api/status"))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
  }

  @Test
  void returnsThreeComponentsInOrderBackendDatabaseMlService() throws Exception {
    stub(HealthStatus.UP, HealthStatus.DOWN);
    mockMvc
        .perform(get("/api/status"))
        .andExpect(jsonPath("$.components.length()").value(3))
        .andExpect(jsonPath("$.components[0].name").value("backend"))
        .andExpect(jsonPath("$.components[1].name").value("database"))
        .andExpect(jsonPath("$.components[2].name").value("ml-service"));
  }

  @Test
  void serializesStatusAsUpOrDown() throws Exception {
    stub(HealthStatus.UP, HealthStatus.DOWN);
    mockMvc
        .perform(get("/api/status"))
        .andExpect(jsonPath("$.components[0].status").value("UP"))
        .andExpect(jsonPath("$.components[1].status").value("UP"))
        .andExpect(jsonPath("$.components[2].status").value("DOWN"));
  }

  @Test
  void serializesCheckedAtAsIso8601UtcEndingInZ() throws Exception {
    stub(HealthStatus.UP, HealthStatus.UP);
    String body =
        mockMvc.perform(get("/api/status")).andReturn().getResponse().getContentAsString();
    for (int i = 0; i < 3; i++) {
      String value = com.jayway.jsonpath.JsonPath.read(body, "$.components[" + i + "].checkedAt");
      assertThat(value).endsWith("Z");
      assertThat(Instant.parse(value)).isEqualTo(CHECKED_AT);
    }
  }

  @Test
  void rootObjectHasOnlyComponentsKey() throws Exception {
    stub(HealthStatus.UP, HealthStatus.UP);
    mockMvc
        .perform(get("/api/status"))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$.components").isArray());
  }

  @Test
  void eachComponentHasOnlyNameStatusAndCheckedAt() throws Exception {
    stub(HealthStatus.UP, HealthStatus.UP);
    mockMvc
        .perform(get("/api/status"))
        .andExpect(jsonPath("$.components[0].length()").value(3))
        .andExpect(jsonPath("$.components[1].length()").value(3))
        .andExpect(jsonPath("$.components[2].length()").value(3))
        .andExpect(jsonPath("$.components[0].name").exists())
        .andExpect(jsonPath("$.components[0].status").exists())
        .andExpect(jsonPath("$.components[0].checkedAt").exists());
  }
}
