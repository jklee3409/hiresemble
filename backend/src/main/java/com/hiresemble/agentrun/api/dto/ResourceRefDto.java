package com.hiresemble.agentrun.api.dto;

import java.util.UUID;

public record ResourceRefDto(String resourceType, UUID resourceId, String displayLabel) {}
