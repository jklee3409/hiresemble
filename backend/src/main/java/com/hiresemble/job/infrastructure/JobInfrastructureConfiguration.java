package com.hiresemble.job.infrastructure;

import com.hiresemble.job.application.port.JobPageFetchGateway;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
    JobPageFetchProperties.class,
    JobDeadlineSchedulerProperties.class,
    JobAiCostProperties.class
})
public class JobInfrastructureConfiguration {

    @Bean
    JobPageFetchGateway jobPageFetchGateway(JobPageFetchProperties properties) {
        properties.validate();
        return new SecureJobPageFetchAdapter(properties);
    }

}
