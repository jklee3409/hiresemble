package com.hiresemble.agentrun.application;

import com.hiresemble.agentrun.domain.SafeError;

public record SafeInterruption(SafeError error, boolean retryable) {}
