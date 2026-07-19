package com.hiresemble.profile.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ProfileP4PortConfiguration {

    @Bean
    @ConditionalOnMissingBean(EvidenceReferenceQueryPort.class)
    EvidenceReferenceQueryPort noEvidenceReferences() {
        return (userId, evidenceId) -> false;
    }
}
