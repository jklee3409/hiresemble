package com.hiresemble.agentrun.application;

import com.hiresemble.agentrun.infrastructure.AiPreferenceStore;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AiPreferenceRegistrationService {

    private final AiPreferenceStore store;

    public AiPreferenceRegistrationService(AiPreferenceStore store) {
        this.store = store;
    }

    public void createDefaultPreference(UUID userId, Instant now) {
        store.createDefault(userId, now);
    }
}
