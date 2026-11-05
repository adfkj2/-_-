package com.scu.sso.auth.web;

import com.scu.sso.auth.audit.AuditWriter;
import com.scu.sso.auth.domain.AppUser;
import com.scu.sso.auth.domain.AuthSession;
import com.scu.sso.auth.repository.AppUserRepository;
import com.scu.sso.auth.session.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 退出接口（F04）：scope=local 仅结束当前会话，scope=global 撤销该账号全部会话。
 * 页面上的退出按钮走 Spring Security 的 /logout，本接口供脚本与其它端调用。
 */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class LogoutApiController {

    private final AppUserRepository userRepository;
    private final SessionService sessionService;
    private final AuditWriter auditWriter;

    @PostMapping("/logout")
    public Map<String, Object> logout(@RequestParam(defaultValue = "local") String scope,
                                      Authentication authentication,
                                      HttpServletRequest request) {
        AppUser user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "ERR-AUTH-04", "登录已失效，请重新登录"));
        HttpSession httpSession = request.getSession(false);
        int revoked;
        String eventType;
        if ("global".equalsIgnoreCase(scope)) {
            revoked = sessionService.revokeAllOfUser(user.getId(), "LOGOUT_ALL_DEVICES");
            eventType = "LOGOUT_GLOBAL";
        } else {
            // 局部退出：只结束当前中心会话并吊销其授权，其它浏览器的会话不受影响（设计 4.2）
            AuthSession current = httpSession == null
                    ? null
                    : sessionService.findBySid(httpSession.getId()).orElse(null);
            if (current != null) {
                sessionService.revoke(current, "LOGOUT_LOCAL");
                revoked = 1;
            } else {
                revoked = 0;
            }
            eventType = "LOGOUT_LOCAL";
        }
        if (httpSession != null) {
            httpSession.invalidate();
        }
        auditWriter.record(eventType, "SUCCESS", user.getId(), request, "scope=" + scope);
        return Map.of("scope", scope, "revoked", revoked);
    }
}
