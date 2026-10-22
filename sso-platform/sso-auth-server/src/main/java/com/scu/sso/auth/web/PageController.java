package com.scu.sso.auth.web;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 页面入口：登录页与登录后的个人首页。
 */
@Controller
public class PageController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    public String index(Authentication authentication, Model model) {
        model.addAttribute("username", authentication.getName());
        return "index";
    }
}
