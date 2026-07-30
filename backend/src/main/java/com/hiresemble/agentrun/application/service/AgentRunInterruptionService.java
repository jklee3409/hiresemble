package com.hiresemble.agentrun.application.service;

import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.application.model.SafeInterruption;
import com.hiresemble.agentrun.application.port.AgentRunStatePort;
import com.hiresemble.agentrun.application.port.ResourceCompensationPort;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentRunInterruptionService {

    private final AgentRunStatePort statePort;
    private final ObjectProvider<ResourceCompensationPort> compensationPorts;

    public AgentRunInterruptionService(
            AgentRunStatePort statePort,
            ObjectProvider<ResourceCompensationPort> compensationPorts) {
        this.statePort = statePort;
        this.compensationPorts = compensationPorts;
    }

    @Transactional
    public AgentRunSnapshot interruptExpired(
            UUID agentRunId, Instant now, SafeInterruption interruption) {
        AgentRunSnapshot interrupted =
                statePort.interruptExpired(agentRunId, now, interruption);
        compensate(interrupted);
        return interrupted;
    }

    private void compensate(AgentRunSnapshot run) {
        if (run.resourceType() == null) {
            return;
        }
        ResourceCompensationPort compensationPort = compensationPorts.orderedStream()
                .filter(port -> port.supports(run.resourceType()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT));
        compensationPort.compensate(
                run.userId(), run.id(), run.resourceType(), run.resourceId());
    }
}
