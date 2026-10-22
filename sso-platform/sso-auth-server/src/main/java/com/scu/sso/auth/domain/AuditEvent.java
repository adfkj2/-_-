package com.scu.sso.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * 审计记录（audit_event）。审计查询与统计属于 F15/F16，本模块只负责写入。
 */
@Getter
@Setter
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_key", nullable = false, unique = true, length = 100)
    private String eventKey;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "event_type", nullable = false, length = 48)
    private String eventType;

    @Column(nullable = false, length = 16)
    private String result;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    @Column(name = "source_masked", length = 100)
    private String sourceMasked;

    @Column(name = "detail_json", columnDefinition = "json")
    private String detailJson;
}
