package com.scu.sso.auth.session;

import java.time.Instant;

/**
 * 会话时限判定（BR-12）：空闲与绝对时限任一达到上限即过期，边界按大于或等于判断。
 */
public final class SessionPolicy {

    private SessionPolicy() {
    }

    /**
     * @param now                服务端当前时间
     * @param lastActiveAt       最近活动时间
     * @param idleTimeoutSeconds 创建时快照的空闲时限
     * @param absoluteExpiresAt  绝对截止时间
     * @return 是否已过期
     */
    public static boolean isExpired(Instant now, Instant lastActiveAt, int idleTimeoutSeconds, Instant absoluteExpiresAt) {
        if (now == null || lastActiveAt == null || absoluteExpiresAt == null) {
            return true;
        }
        boolean idleExpired = !now.isBefore(lastActiveAt.plusSeconds(idleTimeoutSeconds));
        boolean absoluteExpired = !now.isBefore(absoluteExpiresAt);
        return idleExpired || absoluteExpired;
    }
}
