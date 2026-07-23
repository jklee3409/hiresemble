package com.hiresemble.agentrun.application.model;

import com.hiresemble.agentrun.domain.model.SafeError;

public record SafeInterruption(SafeError error, boolean retryable) {}
