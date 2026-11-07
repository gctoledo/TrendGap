package io.github.gctoledo.trendgap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

  private final ApplicationModules modules = ApplicationModules.of(TrendGapApplication.class);

  @Test
  void moduleStructureIsValid() {
    modules.verify();
  }

  @Test
  void platformModuleExists() {
    assertThat(modules.getModuleByName("platform")).isPresent();
  }
}
