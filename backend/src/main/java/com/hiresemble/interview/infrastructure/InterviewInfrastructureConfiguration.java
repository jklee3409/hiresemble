package com.hiresemble.interview.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InterviewAiCostProperties.class)
public class InterviewInfrastructureConfiguration {}
