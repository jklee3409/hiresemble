package com.hiresemble.agentrun.application.port;

import com.hiresemble.agentrun.application.command.DomainResultCommand;

public interface DomainResultApplyPort {
    ApplyResult apply(DomainResultCommand command);

    enum ApplyResult {
        APPLIED,
        ALREADY_APPLIED
    }
}
