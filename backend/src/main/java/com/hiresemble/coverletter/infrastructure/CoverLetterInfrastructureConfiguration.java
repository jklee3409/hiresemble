package com.hiresemble.coverletter.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CoverLetterAiCostProperties.class)
public class CoverLetterInfrastructureConfiguration {}
