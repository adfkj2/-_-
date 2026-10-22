package com.scu.sso.auth.security;

import com.scu.sso.auth.audit.AuditWriter;
import com.scu.sso.auth.domain.AppUser;
import com.scu.sso.auth.repository.AppUserRepository;
import com.scu.sso.auth.session.SessionService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * 登录成功处理（F01）：建立中心会话并写审计，随后回到原访问目标。
 */
@Component
@RequiredArgsConstructor
public class SsoAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final AppUserRepository userRepository;
    private final SessionService sessionService;
    private final AuditWriter auditWriter;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        AppUser user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("登录主体不存在：" + authentication.getName()));
        sessionService.create(user, request, Instant.now());
        auditWriter.record("LOGIN_SUCCESS", "SUCCESS", user.getId(), request, "user=" + user.getUsername());
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
