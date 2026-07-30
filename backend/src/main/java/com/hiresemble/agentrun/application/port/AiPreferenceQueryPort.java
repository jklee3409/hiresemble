package com.hiresemble.agentrun.application.port;

import java.util.UUID;

public interface AiPreferenceQueryPort {

    AiPreferenceSnapshot activePreference(UUID userId);

    record AiPreferenceSnapshot(boolean highQualityEnabled, long version) {}
}
