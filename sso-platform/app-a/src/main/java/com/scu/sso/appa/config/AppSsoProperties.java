package com.scu.sso.appa.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 应用侧配置：中心地址、客户端身份、本地演示账号与内省参数。
 * 演示账号只存在于配置文件与进程内存，不落库、不进入认证中心（设计 4.6）。
 */
@ConfigurationProperties(prefix = "sso")
public record AppSsoProperties(
        String centerBaseUrl,
        String appId,
        String appSecret,
        Demo demo,
        Introspection introspection) {

    public record Demo(Map<String, String> accounts) {
    }

    public record Introspection(String uri, int connectTimeoutMs, int readTimeoutMs) {
    }

    public Map<String, String> accounts() {
        return demo == null || demo.accounts() == null ? Map.of() : demo.accounts();
    }
}
