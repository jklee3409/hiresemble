package com.hiresemble.agentrun.domain.model;

public record RequiredUserAction(
        RequiredUserActionType type,
        ResourceReference resource,
        String route,
        String message) {

    public RequiredUserAction {
        if (type == null) {
            throw new IllegalArgumentException("required action type is required");
        }
        if (route != null && (!route.startsWith("/") || route.startsWith("//")
                || route.contains("://") || route.length() > 500)) {
            throw new IllegalArgumentException("required action route must be same-origin");
        }
        if (message == null || message.isBlank() || message.length() > 500
                || message.indexOf('\n') >= 0 || message.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("required action message is invalid");
        }
    }
}
