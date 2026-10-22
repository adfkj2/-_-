package com.scu.sso.auth.session;

/**
 * 会话状态取值，与数据字典 auth_session.status 一致。
 */
public final class SessionStatus {

    public static final String ACTIVE = "ACTIVE";
    public static final String EXPIRED = "EXPIRED";
    public static final String REVOKED = "REVOKED";

    private SessionStatus() {
    }
}
