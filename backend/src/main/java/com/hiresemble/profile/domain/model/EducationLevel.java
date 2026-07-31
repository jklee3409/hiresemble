package com.hiresemble.profile.domain.model;

public enum EducationLevel {
    OTHER(0),
    HIGH_SCHOOL(10),
    ASSOCIATE(20),
    BACHELOR(30),
    MASTER(40),
    DOCTORATE(50);

    private final int rank;

    EducationLevel(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }
}
