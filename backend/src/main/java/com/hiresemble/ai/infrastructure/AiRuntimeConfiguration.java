package com.hiresemble.ai.infrastructure;

import com.hiresemble.agentrun.application.AgentRunCancellationPort;
import com.hiresemble.agentrun.application.AgentRunLeaseHeartbeatPort;
import com.hiresemble.agentrun.application.AgentRunQueryPort;
import com.hiresemble.agentrun.application.AgentRunStatePort;
import com.hiresemble.agentrun.application.AgentStepCheckpointPort;
import com.hiresemble.agentrun.application.DomainResultApplyPort;
import com.hiresemble.agentrun.application.UsageRecorderPort;
import com.hiresemble.agentrun.domain.WorkflowType;
import com.hiresemble.ai.budget.BudgetGuard;
import com.hiresemble.ai.context.ContextBuilder;
import com.hiresemble.ai.context.DocumentIngestionContextBuilder;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.model.ModelRouter;
import com.hiresemble.ai.model.ModelRouter.ModelPolicy;
import com.hiresemble.ai.model.PolicyModelRouter;
import com.hiresemble.ai.orchestration.AgentOrchestrator;
import com.hiresemble.ai.orchestration.WorkflowFailureHandler;
import com.hiresemble.ai.port.ChatGateway;
import com.hiresemble.ai.port.EmbeddingGateway;
import com.hiresemble.ai.port.WebSearchGateway;
import com.hiresemble.ai.prompt.DocumentIngestionPromptDefinitions;
import com.hiresemble.ai.prompt.PromptRegistry;
import com.hiresemble.ai.validation.StructuredOutputValidator;
import com.hiresemble.ai.workflow.CanonicalWorkflowDefinitions;
import com.hiresemble.ai.workflow.WorkflowRegistry;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import com.hiresemble.ai.workflow.document.DocumentIngestionFailureHandler;
import com.hiresemble.ai.workflow.document.DocumentIngestionWorkflow;
import com.hiresemble.document.application.DocumentWorkflowCommandPort;
import com.hiresemble.document.application.DocumentWorkflowQueryPort;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import tools.jackson.databind.ObjectMapper;

/** Activates only the P4 document contribution; default gateways remain network-free and disabled. */
@Configuration(proxyBeanMethods = false)
public class AiRuntimeConfiguration {

    @Bean
    DocumentIngestionWorkflow documentIngestionWorkflow(
            DocumentWorkflowQueryPort queryPort,
            DocumentWorkflowCommandPort commandPort,
            ObjectMapper objectMapper) {
        return new DocumentIngestionWorkflow(queryPort, commandPort, objectMapper);
    }

    @Bean
    WorkflowRegistry workflowRegistry(DocumentIngestionWorkflow documentWorkflow) {
        return new WorkflowRegistry(
                CanonicalWorkflowDefinitions.all(), List.of(documentWorkflow.contribution()));
    }

    @Bean
    PromptRegistry promptRegistry() {
        return new PromptRegistry(DocumentIngestionPromptDefinitions.all());
    }

    @Bean
    ContextBuilder contextBuilder(
            DocumentWorkflowQueryPort queryPort, Environment environment) {
        long version = environment.getProperty(
                "hiresemble.ai.model-policy-version", Long.class, 1L);
        return new DocumentIngestionContextBuilder(queryPort, version);
    }

    @Bean
    ModelRouter modelRouter(Environment environment) {
        String provider = textOrNone(environment.getProperty("hiresemble.ai.provider"));
        String balanced = textOrNone(environment.getProperty("hiresemble.ai.model-balanced"));
        String lowCost = textOrNone(environment.getProperty("hiresemble.ai.model-low-cost"));
        String highQuality = textOrNone(environment.getProperty("hiresemble.ai.model-high-quality"));
        if ("none".equals(lowCost)) lowCost = balanced;
        boolean enabled = !"none".equalsIgnoreCase(provider);
        long version = environment.getProperty(
                "hiresemble.ai.model-policy-version", Long.class, 1L);
        return new PolicyModelRouter(new ModelPolicy(
                version,
                enabled,
                provider,
                lowCost,
                balanced,
                highQuality,
                Set.of(
                        WorkflowType.COVER_LETTER_GENERATION,
                        WorkflowType.COVER_LETTER_VERIFICATION,
                        WorkflowType.INTERVIEW_ANSWER_FEEDBACK)));
    }

    @Bean
    StructuredOutputValidator structuredOutputValidator(ObjectMapper objectMapper) {
        return new StructuredOutputValidator(objectMapper);
    }

    @Bean
    BudgetGuard budgetGuard(com.hiresemble.agentrun.application.BudgetReservationPort port) {
        return new BudgetGuard(port);
    }

    @Bean
    WorkflowFailureHandler documentWorkflowFailureHandler(
            DocumentWorkflowQueryPort queryPort,
            DocumentWorkflowCommandPort commandPort) {
        return new DocumentIngestionFailureHandler(queryPort, commandPort);
    }

    @Bean
    @ConditionalOnProperty(
            name = "hiresemble.ai.runtime.enabled",
            havingValue = "true",
            matchIfMissing = true)
    AgentOrchestrator agentOrchestrator(
            WorkflowRegistry workflowRegistry,
            ContextBuilder contextBuilder,
            ModelRouter modelRouter,
            PromptRegistry promptRegistry,
            StructuredOutputValidator outputValidator,
            ChatGateway chatGateway,
            EmbeddingGateway embeddingGateway,
            WebSearchGateway webSearchGateway,
            AgentRunQueryPort runQueryPort,
            AgentRunStatePort runStatePort,
            AgentStepCheckpointPort stepCheckpointPort,
            UsageRecorderPort usageRecorderPort,
            AgentRunCancellationPort cancellationPort,
            AgentRunLeaseHeartbeatPort leaseHeartbeatPort,
            BudgetGuard budgetGuard,
            ObjectMapper objectMapper,
            Clock clock,
            ObjectProvider<WorkflowFailureHandler> failureHandlers) {
        DomainResultApplyPort unsupportedGenericApply = command -> {
            throw AiExecutionException.nonRetryable(
                    FailureKind.CONFIGURATION,
                    "AI_GENERIC_DOMAIN_APPLY_NOT_CONFIGURED",
                    "AI 결과 적용 구성이 준비되지 않았습니다.");
        };
        return new AgentOrchestrator(
                workflowRegistry,
                contextBuilder,
                modelRouter,
                promptRegistry,
                outputValidator,
                chatGateway,
                embeddingGateway,
                webSearchGateway,
                runQueryPort,
                runStatePort,
                stepCheckpointPort,
                usageRecorderPort,
                unsupportedGenericApply,
                cancellationPort,
                leaseHeartbeatPort,
                budgetGuard,
                objectMapper,
                clock,
                failureHandlers.orderedStream().toList());
    }

    private String textOrNone(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }
}
