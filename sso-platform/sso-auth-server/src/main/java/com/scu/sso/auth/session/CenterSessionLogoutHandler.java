package com.scu.sso.auth.session;

import com.scu.sso.auth.audit.AuditWriter;
import com.scu.sso.auth.domain.AuthSession;
import com.scu.sso.auth.repository.AuthSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

/**
 * 统一退出（F04、BR-17）：撤销当前浏览器的中心会话，其他浏览器会话不受影响。
 */
@Component
@RequiredArgsConstructor
public class CenterSessionLogoutHandler implements LogoutHandler {

    private final AuthSessionRepository sessionRepository;
    private final SessionService sessionService;
    private final AuditWriter auditWriter;

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        HttpSession httpSession = request.getSession(false);
        if (httpSession == null) {
            return;
        }
        AuthSession session = sessionRepository.findById(httpSession.getId()).orElse(null);
        if (session == null) {
            return;
        }
        sessionService.revoke(session, "LOGOUT_GLOBAL");
        auditWriter.record("LOGOUT_GLOBAL", "SUCCESS", session.getUserId(), request, "sid=" + session.getSid());
    }
}
