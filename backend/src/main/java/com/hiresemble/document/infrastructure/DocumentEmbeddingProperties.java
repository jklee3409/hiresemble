package com.hiresemble.document.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hiresemble.ai.embedding")
public class DocumentEmbeddingProperties {

    private int dimension = 1536;

    public int getDimension() {
        return dimension;
    }

    public void setDimension(int dimension) {
        this.dimension = dimension;
    }
}
