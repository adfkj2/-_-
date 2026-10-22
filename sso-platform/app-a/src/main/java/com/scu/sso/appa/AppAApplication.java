package com.scu.sso.appa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 教务信息服务 A：作为 OIDC 客户端接入认证中心，用于验证跨系统单点登录。
 */
@SpringBootApplication
public class AppAApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppAApplication.class, args);
    }
}
