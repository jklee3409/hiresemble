package com.hiresemble.document.infrastructure;

import com.hiresemble.document.infrastructure.config.DocumentEmbeddingPolicyValidator;
import com.hiresemble.document.infrastructure.config.DocumentEmbeddingProperties;
import com.hiresemble.document.infrastructure.persistence.DocumentStore;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.hiresemble.document.domain.model.DocumentRecords.EmbeddingPolicy;
import org.junit.jupiter.api.Test;

class DocumentEmbeddingPolicyValidatorTest {

    @Test
    void configuredDimensionMismatchFailsBeforeDatabaseUse() {
        DocumentEmbeddingProperties properties = new DocumentEmbeddingProperties();
        properties.setDimension(768);
        DocumentStore store = mock(DocumentStore.class);

        assertThatThrownBy(() -> new DocumentEmbeddingPolicyValidator(properties, store)
                        .afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DOCUMENT_EMBEDDING_DIMENSION_MISMATCH");
        verifyNoInteractions(store);
    }

    @Test
    void activePolicyProviderModelDimensionAndDistanceMustMatch() {
        DocumentEmbeddingProperties properties = new DocumentEmbeddingProperties();
        DocumentStore store = mock(DocumentStore.class);
        when(store.activeEmbeddingPolicy())
                .thenReturn(new EmbeddingPolicy(1, "OpenAI", "other-model", 1536, "COSINE", 1));

        assertThatThrownBy(() -> new DocumentEmbeddingPolicyValidator(properties, store)
                        .afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DOCUMENT_EMBEDDING_POLICY_MISMATCH");
    }
}
