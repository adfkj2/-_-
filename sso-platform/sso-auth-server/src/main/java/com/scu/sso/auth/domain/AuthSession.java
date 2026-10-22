package com.scu.sso.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * 中心会话（auth_session）。会话标识即容器会话 ID，时限在创建时快照。
 */
@Getter
@Setter
@Entity
@Table(name = "auth_session")
public class AuthSession {

    @Id
    @Column(length = 64)
    private String sid;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "http_session_digest", nullable = false, length = 64)
    private String httpSessionDigest;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_active_at", nullable = false)
    private Instant lastActiveAt;

    @Column(name = "absolute_expires_at", nullable = false)
    private Instant absoluteExpiresAt;

    @Column(name = "idle_timeout_seconds", nullable = false)
    private int idleTimeoutSeconds;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoke_reason", length = 64)
    private String revokeReason;

    @Column(name = "device_summary", nullable = false, length = 200)
    private String deviceSummary;

    @Column(name = "policy_version", nullable = false)
    private long policyVersion;

    @Column(name = "user_auth_version", nullable = false)
    private long userAuthVersion;
}
