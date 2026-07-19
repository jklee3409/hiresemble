package com.hiresemble.agentrun.application;

import java.util.UUID;

public interface UsageRecorderPort {
    UUID record(UsageRecordCommand command);
}
