package com.scu.sso.appb.web;

import java.time.Instant;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 图书信息服务 B 的演示页面与受保护接口。
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home(@AuthenticationPrincipal OidcUser user, Model model) {
        model.addAttribute("appName", "图书信息服务 B");
        model.addAttribute("username", user.getPreferredUsername() != null
                ? user.getPreferredUsername() : user.getSubject());
        model.addAttribute("subject", user.getSubject());
        return "home";
    }

    /**
     * 受保护接口：未认证访问返回 401，不泄露任何业务数据。
     */
    @GetMapping("/api/books")
    @ResponseBody
    public Map<String, Object> protectedData(@AuthenticationPrincipal OidcUser user) {
        return Map.of(
                "app", "图书信息服务 B",
                "subject", user.getSubject(),
                "servedAt", Instant.now().toString());
    }
}
