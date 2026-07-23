package com.hiresemble.agentrun.application.port;

import com.hiresemble.agentrun.application.command.UsageRecordCommand;
import java.util.UUID;

public interface UsageRecorderPort {
    UUID record(UsageRecordCommand command);
}
