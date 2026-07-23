package com.hiresemble.profile.application.service;

import com.hiresemble.profile.infrastructure.persistence.ProfileStore;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ProfileRegistrationService {

    private final ProfileStore store;

    public ProfileRegistrationService(ProfileStore store) {
        this.store = store;
    }

    public void createDefaultProfile(UUID userId, Instant now) {
        store.createDefaultProfile(userId, now);
    }
}
