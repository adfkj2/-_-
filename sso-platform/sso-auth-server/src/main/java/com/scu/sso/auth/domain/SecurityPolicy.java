package com.scu.sso.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * 认证安全策略（security_policy）。单例记录，策略编辑属于 F17。
 */
@Getter
@Setter
@Entity
@Table(name = "security_policy")
public class SecurityPolicy {

    @Id
    private Long id;

    @Column(name = "idle_timeout_minutes", nullable = false)
    private int idleTimeoutMinutes;

    @Column(name = "absolute_timeout_hours", nullable = false)
    private int absoluteTimeoutHours;

    @Column(name = "failure_window_minutes", nullable = false)
    private int failureWindowMinutes;

    @Column(name = "failure_threshold", nullable = false)
    private int failureThreshold;

    @Column(name = "lock_minutes", nullable = false)
    private int lockMinutes;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private long version;
}
