package com.hiresemble.agentrun.api.dto;

import com.hiresemble.agentrun.domain.model.RequiredUserActionType;

public record RequiredUserActionDto(
        RequiredUserActionType type,
        ResourceRefDto resource,
        String route,
        String message) {}
