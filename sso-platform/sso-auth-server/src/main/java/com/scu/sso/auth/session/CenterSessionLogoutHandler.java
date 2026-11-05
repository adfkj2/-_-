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
 * 统一退出（F04、BR-17）：撤销该账号的全部中心会话并吊销其全部应用授权，
 * 其他浏览器再次访问应用时必须重新认证。局部退出走 /api/me/logout?scope=local。
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
        int revoked = sessionService.revokeAllOfUser(session.getUserId(), "LOGOUT_ALL_DEVICES");
        auditWriter.record("LOGOUT_GLOBAL", "SUCCESS", session.getUserId(), request,
                "sid=" + session.getSid() + ";revoked=" + revoked);
    }
}
