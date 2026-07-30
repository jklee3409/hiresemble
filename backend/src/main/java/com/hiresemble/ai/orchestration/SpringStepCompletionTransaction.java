package com.hiresemble.ai.orchestration;

import java.util.Objects;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Spring transaction implementation used by the production Agent Orchestrator wiring. */
public final class SpringStepCompletionTransaction
        implements StepCompletionTransaction {

    private final TransactionTemplate transactionTemplate;

    public SpringStepCompletionTransaction(
            PlatformTransactionManager transactionManager) {
        this.transactionTemplate =
                new TransactionTemplate(Objects.requireNonNull(transactionManager));
        this.transactionTemplate.setIsolationLevel(
                TransactionDefinition.ISOLATION_SERIALIZABLE);
    }

    @Override
    public void execute(Runnable work) {
        Objects.requireNonNull(work, "work");
        transactionTemplate.executeWithoutResult(ignored -> work.run());
    }
}
