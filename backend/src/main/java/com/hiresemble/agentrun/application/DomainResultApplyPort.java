package com.hiresemble.agentrun.application;

public interface DomainResultApplyPort {
    ApplyResult apply(DomainResultCommand command);

    enum ApplyResult {
        APPLIED,
        ALREADY_APPLIED
    }
}
