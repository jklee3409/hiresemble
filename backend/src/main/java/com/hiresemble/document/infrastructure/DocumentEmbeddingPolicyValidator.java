package com.hiresemble.document.infrastructure;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

@Component
public final class DocumentEmbeddingPolicyValidator implements SmartInitializingSingleton {

    private static final int DATABASE_DIMENSION = 1536;
    private final DocumentEmbeddingProperties properties;
    private final DocumentStore store;

    public DocumentEmbeddingPolicyValidator(
            DocumentEmbeddingProperties properties, DocumentStore store) {
        this.properties = properties;
        this.store = store;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (properties.getDimension() != DATABASE_DIMENSION) {
            throw new IllegalStateException("DOCUMENT_EMBEDDING_DIMENSION_MISMATCH");
        }
        var policy = store.activeEmbeddingPolicy();
        if (policy.dimension() != DATABASE_DIMENSION
                || !"OpenAI".equals(policy.provider())
                || !"text-embedding-3-small".equals(policy.model())
                || !"COSINE".equals(policy.distance())) {
            throw new IllegalStateException("DOCUMENT_EMBEDDING_POLICY_MISMATCH");
        }
    }
}
