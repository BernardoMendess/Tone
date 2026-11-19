package com.tone.domain.entity;

import com.tone.domain.enums.UserRole;
import jakarta.persistence.*;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    private static final int MAX_FAILED_LOGINS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 180)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion = 0;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    public void registerFailedLogin(Instant now) {
        if (lockedUntil != null && !now.isBefore(lockedUntil)) {
            this.failedLoginAttempts = 0;
            this.lockedUntil = null;
        }
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= MAX_FAILED_LOGINS) {
            this.lockedUntil = now.plus(LOCKOUT_DURATION);
        }
        this.updatedAt = now;
    }

    public void resetFailedLogins(Instant now) {
        if (this.failedLoginAttempts != 0 || this.lockedUntil != null) {
            this.failedLoginAttempts = 0;
            this.lockedUntil = null;
            this.updatedAt = now;
        }
    }

    public void revokeAllTokens() {
        this.tokenVersion++;
    }

    public void promoteToCreator() {
        if (this.role == UserRole.BUYER) {
            this.role = UserRole.CREATOR;
        }
    }

    public void demoteToBuyer() {
        if (this.role == UserRole.CREATOR) {
            this.role = UserRole.BUYER;
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getTokenVersion() { return tokenVersion; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
