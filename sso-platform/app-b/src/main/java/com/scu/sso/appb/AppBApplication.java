package com.scu.sso.appb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 图书信息服务 B：作为 OIDC 客户端接入认证中心，用于验证跨系统单点登录。
 */
@SpringBootApplication
public class AppBApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppBApplication.class, args);
    }
}
