package com.hiresemble.ai.infrastructure;

import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.port.AiGatewayResponse;
import com.hiresemble.ai.port.ChatGateway;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "hiresemble.ai.provider", havingValue = "none", matchIfMissing = true)
public final class DisabledChatGateway implements ChatGateway {
    @Override
    public AiGatewayResponse chat(ChatRequest request) {
        throw disabled();
    }

    static AiExecutionException disabled() {
        return AiExecutionException.nonRetryable(
                FailureKind.CONFIGURATION,
                "AI_PROVIDER_DISABLED",
                "AI 실행 공급자가 활성화되지 않았습니다.");
    }
}
