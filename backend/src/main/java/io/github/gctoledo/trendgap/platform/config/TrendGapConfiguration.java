package io.github.gctoledo.trendgap.platform.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Enables the validated TrendGap configuration properties. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TrendGapProperties.class)
class TrendGapConfiguration {}
