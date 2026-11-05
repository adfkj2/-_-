package com.scu.sso.appb.web;

import com.scu.sso.appb.config.AppSsoProperties;
import com.scu.sso.appb.security.AuthModeFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 应用侧登录页与本地演示登录（I-WEB-01、I-WEB-02、I-WEB-04）。
 * 本地账号只来自配置内存，与认证中心账号体系无关；本地会话不能访问受保护资源（设计 4.6）。
 */
@Controller
@RequiredArgsConstructor
public class LoginController {

    private static final String APP_NAME = "图书信息服务 B";

    private final AppSsoProperties properties;

    /**
     * 登录页：同时给出本地演示登录与统一认证登录两个入口。
     */
    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error,
                        @RequestParam(required = false) String logout,
                        @RequestParam(required = false) String needSso,
                        @RequestParam(required = false) String expired,
                        Model model) {
        model.addAttribute("appName", APP_NAME);
        model.addAttribute("demoUsers", properties.accounts().keySet());
        model.addAttribute("loginError", error != null);
        model.addAttribute("loggedOut", logout != null);
        model.addAttribute("needSso", needSso != null);
        model.addAttribute("expired", expired != null);
        return "login";
    }

    /**
     * 本地演示登录：校验内存账号，建立 authMode=LOCAL 的应用会话，只可进入本地演示页。
     */
    @PostMapping("/login/local")
    public String localLogin(@RequestParam String username,
                             @RequestParam String password,
                             HttpServletRequest request,
                             HttpServletResponse response) {
        String account = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        String expected = properties.accounts().get(account);
        if (expected == null || password == null
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                password.getBytes(StandardCharsets.UTF_8))) {
            return "redirect:/login?error=1";
        }
        HttpSession session = request.getSession(true);
        // 防会话固定：登录成功后更换会话标识
        request.changeSessionId();
        session.setAttribute(AuthModeFilter.AUTH_MODE, AuthModeFilter.MODE_LOCAL);
        session.setAttribute(AuthModeFilter.LOCAL_USER, account);
        session.removeAttribute(AuthModeFilter.ACCESS_TOKEN);

        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                account, null, List.of(new SimpleGrantedAuthority("ROLE_LOCAL")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);
        return "redirect:/local-demo";
    }

    /**
     * 本地演示页：展示本地数据并明确标注未通过统一认证。
     */
    @GetMapping("/local-demo")
    public String localDemo(Model model, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String mode = session == null ? null : (String) session.getAttribute(AuthModeFilter.AUTH_MODE);
        if (mode == null) {
            return "redirect:/login";
        }
        model.addAttribute("appName", APP_NAME);
        model.addAttribute("authMode", mode);
        model.addAttribute("username", session.getAttribute(AuthModeFilter.LOCAL_USER));
        return "local-demo";
    }
}
