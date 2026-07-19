package com.hiresemble.agentrun.api;

import java.util.UUID;

public record ResourceRefDto(String resourceType, UUID resourceId, String displayLabel) {}
