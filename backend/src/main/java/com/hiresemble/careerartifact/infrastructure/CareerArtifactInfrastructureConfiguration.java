package com.hiresemble.careerartifact.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CareerArtifactProperties.class)
public class CareerArtifactInfrastructureConfiguration {}
