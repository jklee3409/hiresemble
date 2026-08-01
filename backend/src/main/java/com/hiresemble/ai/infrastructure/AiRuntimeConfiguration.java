package com.hiresemble.ai.infrastructure;

import com.hiresemble.agentrun.application.port.AgentRunCancellationPort;
import com.hiresemble.agentrun.application.port.AgentRunLeaseHeartbeatPort;
import com.hiresemble.agentrun.application.port.AgentRunQueryPort;
import com.hiresemble.agentrun.application.port.AgentRunStatePort;
import com.hiresemble.agentrun.application.port.AgentStepCheckpointPort;
import com.hiresemble.agentrun.application.port.AiPreferenceQueryPort;
import com.hiresemble.agentrun.application.port.DomainResultApplyPort;
import com.hiresemble.agentrun.application.port.UsageRecorderPort;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.budget.BudgetGuard;
import com.hiresemble.ai.context.ContextBuilder;
import com.hiresemble.ai.context.CoverLetterGenerationContextBuilder;
import com.hiresemble.ai.context.CoverLetterVerificationContextBuilder;
import com.hiresemble.ai.context.DocumentIngestionContextBuilder;
import com.hiresemble.ai.context.InterviewFeedbackContextBuilder;
import com.hiresemble.ai.context.InterviewPreparationContextBuilder;
import com.hiresemble.ai.context.JobAnalysisContextBuilder;
import com.hiresemble.ai.context.JobPostingExtractionContextBuilder;
import com.hiresemble.ai.context.WorkflowContextBuilder;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.model.ModelRouter;
import com.hiresemble.ai.model.ModelRouter.ModelPolicy;
import com.hiresemble.ai.model.PolicyModelRouter;
import com.hiresemble.ai.orchestration.AgentOrchestrator;
import com.hiresemble.ai.orchestration.SpringStepCompletionTransaction;
import com.hiresemble.ai.orchestration.StepCompletionTransaction;
import com.hiresemble.ai.orchestration.WorkflowFailureHandler;
import com.hiresemble.ai.port.ChatGateway;
import com.hiresemble.ai.port.ImageTextExtractionGateway;
import com.hiresemble.ai.port.EmbeddingGateway;
import com.hiresemble.ai.port.WebSearchGateway;
import com.hiresemble.ai.prompt.CanonicalPromptDefinitions;
import com.hiresemble.ai.prompt.PromptRegistry;
import com.hiresemble.ai.validation.OpenAiStrictSchemaCompatibilityValidator;
import com.hiresemble.ai.validation.StrictStructuredOutputSchemaGenerator;
import com.hiresemble.ai.validation.StrictStructuredOutputSchemaRegistry;
import com.hiresemble.ai.validation.StructuredOutputValidator;
import com.hiresemble.ai.workflow.CanonicalWorkflowDefinitions;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow;
import com.hiresemble.ai.workflow.CoverLetterVerificationFailureHandler;
import com.hiresemble.ai.workflow.CoverLetterVerificationWorkflow;
import com.hiresemble.ai.workflow.InterviewAnswerFeedbackWorkflow;
import com.hiresemble.ai.workflow.InterviewPreparationFailureHandler;
import com.hiresemble.ai.workflow.InterviewPreparationWorkflow;
import com.hiresemble.ai.workflow.JobAnalysisWorkflow;
import com.hiresemble.ai.workflow.JobPostingExtractionFailureHandler;
import com.hiresemble.ai.workflow.JobPostingExtractionWorkflow;
import com.hiresemble.ai.workflow.WorkflowRegistry;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import com.hiresemble.ai.workflow.document.DocumentIngestionFailureHandler;
import com.hiresemble.ai.workflow.document.DocumentIngestionWorkflow;
import com.hiresemble.coverletter.application.port.CoverLetterCommandPort;
import com.hiresemble.coverletter.application.port.CoverLetterQueryPort;
import com.hiresemble.document.application.port.DocumentWorkflowCommandPort;
import com.hiresemble.document.application.port.DocumentWorkflowQueryPort;
import com.hiresemble.job.application.port.JobPageFetchGateway;
import com.hiresemble.job.application.port.JobAnalysisCommandPort;
import com.hiresemble.job.application.port.JobAnalysisEmbeddingQueryPort;
import com.hiresemble.job.application.port.JobAnalysisQueryPort;
import com.hiresemble.job.application.port.JobWorkflowCommandPort;
import com.hiresemble.job.application.port.JobWorkflowQueryPort;
import com.hiresemble.interview.application.port.InterviewWorkflowCommandPort;
import com.hiresemble.interview.application.port.InterviewWorkflowQueryPort;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

/** Activates bounded Document and Job contributions; model gateways remain disabled by default. */
@Configuration(proxyBeanMethods = false)
public class AiRuntimeConfiguration {

    @Bean
    @ConditionalOnMissingBean(ImageTextExtractionGateway.class)
    ImageTextExtractionGateway disabledImageTextExtractionGateway() {
        return new DisabledImageTextExtractionGateway();
    }

    @Bean
    DocumentIngestionWorkflow documentIngestionWorkflow(
            DocumentWorkflowQueryPort queryPort,
            DocumentWorkflowCommandPort commandPort,
            ObjectMapper objectMapper) {
        return new DocumentIngestionWorkflow(queryPort, commandPort, objectMapper);
    }

    @Bean
    JobPostingExtractionWorkflow jobPostingExtractionWorkflow(
            JobWorkflowQueryPort queryPort,
            JobWorkflowCommandPort commandPort,
            JobPageFetchGateway fetchGateway,
            com.hiresemble.job.application.port.JobImageFetchGateway imageFetchGateway,
            ImageTextExtractionGateway imageTextExtractionGateway,
            com.hiresemble.job.infrastructure.JobPageFetchProperties fetchProperties,
            ObjectMapper objectMapper,
            Clock clock) {
        return new JobPostingExtractionWorkflow(
                queryPort,
                commandPort,
                fetchGateway,
                imageFetchGateway,
                imageTextExtractionGateway,
                fetchProperties,
                objectMapper,
                clock);
    }

    @Bean
    JobAnalysisWorkflow jobAnalysisWorkflow(
            JobAnalysisQueryPort queryPort,
            JobAnalysisCommandPort commandPort,
            JobAnalysisEmbeddingQueryPort embeddingQueryPort,
            ObjectMapper objectMapper) {
        return new JobAnalysisWorkflow(
                queryPort, commandPort, embeddingQueryPort, objectMapper);
    }

    @Bean
    CoverLetterGenerationWorkflow coverLetterGenerationWorkflow(
            CoverLetterQueryPort queryPort,
            CoverLetterCommandPort commandPort,
            JobAnalysisEmbeddingQueryPort embeddingQueryPort,
            ObjectMapper objectMapper) {
        return new CoverLetterGenerationWorkflow(
                queryPort, commandPort, embeddingQueryPort, objectMapper);
    }

    @Bean
    CoverLetterVerificationWorkflow coverLetterVerificationWorkflow(
            CoverLetterQueryPort queryPort,
            CoverLetterCommandPort commandPort,
            ObjectMapper objectMapper) {
        return new CoverLetterVerificationWorkflow(
                queryPort, commandPort, objectMapper);
    }

    @Bean
    InterviewPreparationWorkflow interviewPreparationWorkflow(
            InterviewWorkflowQueryPort queryPort,
            InterviewWorkflowCommandPort commandPort,
            ObjectMapper objectMapper,
            Clock clock) {
        return new InterviewPreparationWorkflow(
                queryPort, commandPort, objectMapper, clock);
    }

    @Bean
    InterviewAnswerFeedbackWorkflow interviewAnswerFeedbackWorkflow(
            InterviewWorkflowQueryPort queryPort,
            InterviewWorkflowCommandPort commandPort,
            ObjectMapper objectMapper) {
        return new InterviewAnswerFeedbackWorkflow(
                queryPort, commandPort, objectMapper);
    }

    @Bean
    WorkflowRegistry workflowRegistry(
            DocumentIngestionWorkflow documentWorkflow,
            JobPostingExtractionWorkflow jobWorkflow,
            JobAnalysisWorkflow jobAnalysisWorkflow,
            CoverLetterGenerationWorkflow coverLetterGenerationWorkflow,
            CoverLetterVerificationWorkflow coverLetterVerificationWorkflow,
            InterviewPreparationWorkflow interviewPreparationWorkflow,
            InterviewAnswerFeedbackWorkflow interviewAnswerFeedbackWorkflow) {
        return new WorkflowRegistry(
                CanonicalWorkflowDefinitions.all(),
                List.of(
                        documentWorkflow.contribution(),
                        jobWorkflow.contribution(),
                        jobAnalysisWorkflow.contribution(),
                        coverLetterGenerationWorkflow.contribution(),
                        coverLetterVerificationWorkflow.contribution(),
                        interviewPreparationWorkflow.contribution(),
                        interviewAnswerFeedbackWorkflow.contribution()));
    }

    @Bean
    PromptRegistry promptRegistry() {
        return new PromptRegistry(CanonicalPromptDefinitions.all());
    }

    @Bean
    OpenAiStrictSchemaCompatibilityValidator openAiStrictSchemaCompatibilityValidator(
            ObjectMapper objectMapper) {
        return new OpenAiStrictSchemaCompatibilityValidator(objectMapper);
    }

    @Bean
    StrictStructuredOutputSchemaGenerator strictStructuredOutputSchemaGenerator(
            ObjectMapper objectMapper) {
        return new StrictStructuredOutputSchemaGenerator(objectMapper);
    }

    @Bean
    StrictStructuredOutputSchemaRegistry strictStructuredOutputSchemaRegistry(
            PromptRegistry promptRegistry,
            StrictStructuredOutputSchemaGenerator generator,
            OpenAiStrictSchemaCompatibilityValidator validator) {
        return new StrictStructuredOutputSchemaRegistry(promptRegistry, generator, validator);
    }

    @Bean
    ContextBuilder contextBuilder(
            DocumentWorkflowQueryPort documentQueryPort,
            JobWorkflowQueryPort jobQueryPort,
            JobAnalysisQueryPort jobAnalysisQueryPort,
            CoverLetterQueryPort coverLetterQueryPort,
            InterviewWorkflowQueryPort interviewQueryPort,
            AiPreferenceQueryPort preferenceQueryPort,
            Environment environment) {
        long version = environment.getProperty(
                "hiresemble.ai.model-policy-version", Long.class, 1L);
        return new WorkflowContextBuilder(
                new DocumentIngestionContextBuilder(documentQueryPort, version),
                new JobPostingExtractionContextBuilder(jobQueryPort, version),
                new JobAnalysisContextBuilder(jobAnalysisQueryPort, version),
                new CoverLetterGenerationContextBuilder(
                        coverLetterQueryPort, preferenceQueryPort, version),
                new CoverLetterVerificationContextBuilder(
                        coverLetterQueryPort, preferenceQueryPort, version),
                new InterviewPreparationContextBuilder(interviewQueryPort, version),
                new InterviewFeedbackContextBuilder(
                        interviewQueryPort, preferenceQueryPort, version));
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
    BudgetGuard budgetGuard(com.hiresemble.agentrun.application.port.BudgetReservationPort port) {
        return new BudgetGuard(port);
    }

    @Bean
    StepCompletionTransaction stepCompletionTransaction(
            PlatformTransactionManager transactionManager) {
        return new SpringStepCompletionTransaction(transactionManager);
    }

    @Bean
    WorkflowFailureHandler documentWorkflowFailureHandler(
            DocumentWorkflowQueryPort queryPort,
            DocumentWorkflowCommandPort commandPort) {
        return new DocumentIngestionFailureHandler(queryPort, commandPort);
    }

    @Bean
    WorkflowFailureHandler jobWorkflowFailureHandler(
            JobWorkflowQueryPort queryPort,
            JobWorkflowCommandPort commandPort) {
        return new JobPostingExtractionFailureHandler(queryPort, commandPort);
    }

    @Bean
    WorkflowFailureHandler coverLetterVerificationFailureHandler(
            CoverLetterCommandPort commandPort) {
        return new CoverLetterVerificationFailureHandler(commandPort);
    }

    @Bean
    WorkflowFailureHandler interviewPreparationFailureHandler(
            InterviewWorkflowCommandPort commandPort) {
        return new InterviewPreparationFailureHandler(commandPort);
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
            ObjectProvider<WorkflowFailureHandler> failureHandlers,
            StepCompletionTransaction stepCompletionTransaction) {
        DomainResultApplyPort domainApply = command -> {
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
                domainApply,
                cancellationPort,
                leaseHeartbeatPort,
                budgetGuard,
                objectMapper,
                clock,
                failureHandlers.orderedStream().toList(),
                stepCompletionTransaction);
    }

    private String textOrNone(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }
}
