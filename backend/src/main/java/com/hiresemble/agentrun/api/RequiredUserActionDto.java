package com.hiresemble.agentrun.api;

import com.hiresemble.agentrun.domain.RequiredUserActionType;

public record RequiredUserActionDto(
        RequiredUserActionType type,
        ResourceRefDto resource,
        String route,
        String message) {}
