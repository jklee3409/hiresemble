package com.hiresemble.auth.infrastructure.persistence;

import com.hiresemble.auth.domain.model.UserRole;
import com.hiresemble.auth.domain.model.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserStatus status;

    @Column(name = "terms_agreed_at", nullable = false)
    private Instant termsAgreedAt;

    @Column(name = "ai_consent_at", nullable = false)
    private Instant aiConsentAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserEntity() {}

    private UserEntity(
            UUID id,
            String email,
            String passwordHash,
            String displayName,
            Instant now) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = UserRole.USER;
        this.status = UserStatus.ACTIVE;
        this.termsAgreedAt = now;
        this.aiConsentAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static UserEntity create(
            UUID id, String email, String passwordHash, String displayName, Instant now) {
        return new UserEntity(id, email, passwordHash, displayName, now);
    }

    public void recordLogin(Instant now) {
        lastLoginAt = now;
        updatedAt = now;
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String displayName() {
        return displayName;
    }

    public UserRole role() {
        return role;
    }

    public UserStatus status() {
        return status;
    }
}
