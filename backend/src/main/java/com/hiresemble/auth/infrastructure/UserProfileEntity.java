package com.hiresemble.auth.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "user_profiles")
public class UserProfileEntity {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserEntity user;

    @Column(name = "legal_name", length = 100)
    private String legalName;

    @Column(length = 2000)
    private String introduction;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "desired_roles", nullable = false, columnDefinition = "jsonb")
    private List<String> desiredRoles = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "desired_industries", nullable = false, columnDefinition = "jsonb")
    private List<String> desiredIndustries = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "desired_locations", nullable = false, columnDefinition = "jsonb")
    private List<String> desiredLocations = new ArrayList<>();

    @Column(name = "expected_graduation_date")
    private LocalDate expectedGraduationDate;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserProfileEntity() {}

    private UserProfileEntity(UUID id, UserEntity user, Instant now) {
        this.id = id;
        this.user = user;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static UserProfileEntity create(UUID id, UserEntity user, Instant now) {
        return new UserProfileEntity(id, user, now);
    }
}
