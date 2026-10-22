package com.scu.sso.auth.security;

import com.scu.sso.auth.audit.AuditWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * 登录失败处理（BR-07）：界面只提示统一文案，真实原因写入审计。
 */
@Component
public class UnifiedLoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final AuditWriter auditWriter;

    public UnifiedLoginFailureHandler(AuditWriter auditWriter) {
        super("/login?error");
        this.auditWriter = auditWriter;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        auditWriter.record("LOGIN_FAILURE", "FAILURE", null, request,
                "reason=" + exception.getClass().getSimpleName());
        super.onAuthenticationFailure(request, response, exception);
    }
}
