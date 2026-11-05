package com.scu.sso.appa.web;

import com.scu.sso.appa.security.AuthModeFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 受保护页面与接口（I-WEB-05）：只有 authMode=SSO 且内省通过的会话可以访问，
 * 判定由 AuthModeFilter 完成，本类只负责渲染数据。
 */
@Controller
public class HomeController {

    private static final String APP_NAME = "教务信息服务 A";

    @GetMapping({"/", "/home"})
    public String home(@AuthenticationPrincipal OidcUser user, HttpServletRequest request, Model model) {
        model.addAttribute("appName", APP_NAME);
        model.addAttribute("username", user.getPreferredUsername() != null
                ? user.getPreferredUsername() : user.getSubject());
        model.addAttribute("subject", user.getSubject());
        model.addAttribute("authMode", authMode(request));
        return "home";
    }

    /**
     * 受保护接口：未统一认证或授权已撤销时由过滤器返回 401/503，不返回业务数据。
     */
    @GetMapping({"/api/courses", "/api/app/courses"})
    @ResponseBody
    public Map<String, Object> protectedData(@AuthenticationPrincipal OidcUser user) {
        return Map.of(
                "app", APP_NAME,
                "subject", user.getSubject(),
                "servedAt", Instant.now().toString());
    }

    /**
     * 当前身份与登录来源，便于演示时确认 authMode。
     */
    @GetMapping("/api/app/me")
    @ResponseBody
    public Map<String, Object> me(@AuthenticationPrincipal OidcUser user, HttpServletRequest request) {
        return Map.of(
                "app", APP_NAME,
                "username", user.getPreferredUsername() != null ? user.getPreferredUsername() : user.getSubject(),
                "subject", user.getSubject(),
                "authMode", String.valueOf(authMode(request)));
    }

    private String authMode(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? AuthModeFilter.MODE_SSO : String.valueOf(session.getAttribute(AuthModeFilter.AUTH_MODE));
    }
}
