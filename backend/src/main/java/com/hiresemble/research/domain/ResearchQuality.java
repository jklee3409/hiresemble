package com.hiresemble.research.domain;

public enum ResearchQuality {
    BASIC,
    ADVANCED;

    public int maxQueries() {
        return this == BASIC ? 2 : 4;
    }

    public int maxResultsPerQuery() {
        return this == BASIC ? 5 : 8;
    }
}
