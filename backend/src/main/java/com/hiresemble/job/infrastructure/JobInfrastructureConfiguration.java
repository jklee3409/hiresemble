package com.hiresemble.job.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
    JobPageFetchProperties.class,
    JobDeadlineSchedulerProperties.class,
    JobAiCostProperties.class,
    JobAnalysisAiCostProperties.class,
    JobAutoAnalysisProperties.class
})
public class JobInfrastructureConfiguration {

    @Bean
    SecureJobPageFetchAdapter jobPageFetchGateway(JobPageFetchProperties properties) {
        properties.validate();
        return new SecureJobPageFetchAdapter(properties);
    }

}
