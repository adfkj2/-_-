package com.scu.sso.auth.web;

import java.time.Instant;

/**
 * 会话列表展示对象，不含会话摘要等内部字段。
 */
public record SessionView(
        String sid,
        String status,
        Instant createdAt,
        Instant lastActiveAt,
        Instant expiresAt,
        String device,
        boolean current) {
}
