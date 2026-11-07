package io.github.gctoledo.trendgap.platform.status.adapter.out.mlservice;

import io.github.gctoledo.trendgap.platform.config.TrendGapProperties;
import io.github.gctoledo.trendgap.platform.mlclient.ApiClient;
import io.github.gctoledo.trendgap.platform.mlclient.api.HealthApi;
import java.util.Objects;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** Wires the generated ML client using the Boot RestClient.Builder (tracing, metrics). */
@Configuration(proxyBeanMethods = false)
public class MlClientConfig {

  @Bean
  HealthApi mlHealthApi(RestClient.Builder builder, TrendGapProperties properties) {
    var settings =
        HttpClientSettings.defaults()
            .withConnectTimeout(properties.ml().timeout())
            .withReadTimeout(properties.ml().timeout());
    String baseUrl = Objects.requireNonNull(properties.ml().baseUrl());
    RestClient restClient =
        builder
            .baseUrl(baseUrl)
            .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
            .build();
    var apiClient = new ApiClient(restClient);
    apiClient.setBasePath(baseUrl);
    return new HealthApi(apiClient);
  }

  @Bean
  MlServiceProbe mlServiceProbe(HealthApi mlHealthApi) {
    return new MlServiceProbe(mlHealthApi);
  }
}
