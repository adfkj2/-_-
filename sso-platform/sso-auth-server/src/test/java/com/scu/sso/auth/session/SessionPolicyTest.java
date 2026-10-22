package com.scu.sso.auth.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * BR-12：空闲 30 分钟、绝对 8 小时，任一达到即过期，边界按大于或等于判定。
 */
class SessionPolicyTest {

    private static final Instant BASE = Instant.parse("2026-10-22T08:00:00Z");
    private static final int IDLE_SECONDS = 1800;

    @Test
    @DisplayName("空闲时长恰好等于上限即视为过期")
    void idleBoundaryCountsAsExpired() {
        Instant now = BASE.plusSeconds(IDLE_SECONDS);
        assertThat(SessionPolicy.isExpired(now, BASE, IDLE_SECONDS, BASE.plusSeconds(28800))).isTrue();
    }

    @Test
    @DisplayName("空闲时长未达上限时仍然有效")
    void withinIdleWindowIsActive() {
        Instant now = BASE.plusSeconds(IDLE_SECONDS - 1);
        assertThat(SessionPolicy.isExpired(now, BASE, IDLE_SECONDS, BASE.plusSeconds(28800))).isFalse();
    }

    @Test
    @DisplayName("绝对时限到期时，即使刚有活动也已过期")
    void absoluteTimeoutWinsOverRecentActivity() {
        Instant absolute = BASE.plusSeconds(28800);
        Instant lastActive = absolute.minusSeconds(60);
        assertThat(SessionPolicy.isExpired(absolute, lastActive, IDLE_SECONDS, absolute)).isTrue();
    }

    @Test
    @DisplayName("缺字段时按过期处理，避免误放行")
    void nullInputsAreTreatedAsExpired() {
        assertThat(SessionPolicy.isExpired(null, BASE, IDLE_SECONDS, BASE.plusSeconds(60))).isTrue();
        assertThat(SessionPolicy.isExpired(BASE, null, IDLE_SECONDS, BASE.plusSeconds(60))).isTrue();
        assertThat(SessionPolicy.isExpired(BASE, BASE, IDLE_SECONDS, null)).isTrue();
    }
}
