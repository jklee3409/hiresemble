package com.hiresemble.agentrun.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SchedulingConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(SchedulingConfiguration.class);

    @Test
    void schedulingIsEnabledByDefaultForProductionCompatibility() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(SchedulingConfiguration.class));
    }

    @Test
    void schedulingCanBeDisabledForDeterministicIntegrationTests() {
        contextRunner
                .withPropertyValues("hiresemble.scheduling.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(SchedulingConfiguration.class));
    }
}
