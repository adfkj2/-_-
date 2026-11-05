package com.scu.sso.auth.config;

import java.time.Duration;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

/**
 * 业务系统客户端定义：机密客户端 + PKCE S256，授权码 60 秒、访问令牌 10 分钟、不签发刷新令牌。
 * 常量取自需求第 3.3 节与测试文档第 3 章，改动需同步文档。
 */
public final class RegisteredClientFactory {

    /** 授权码有效期，对应 BR-24。 */
    public static final Duration AUTHORIZATION_CODE_TTL = Duration.ofSeconds(60);

    /** 访问令牌有效期，对应 BR-24。 */
    public static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(10);

    private RegisteredClientFactory() {
    }

    public static RegisteredClient build(String clientId, String clientName, String redirectUri,
                                         String rawSecret, PasswordEncoder passwordEncoder) {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientName(clientName)
                .clientSecret(passwordEncoder.encode(rawSecret))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(redirectUri)
                .scope("openid")
                .scope("profile")
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .authorizationCodeTimeToLive(AUTHORIZATION_CODE_TTL)
                        .accessTokenTimeToLive(ACCESS_TOKEN_TTL)
                        // 设计 4.1/BR-24：访问令牌必须是不透明令牌，令牌内容只存服务端，可即时吊销
                        .accessTokenFormat(OAuth2TokenFormat.REFERENCE)
                        .reuseRefreshTokens(false)
                        .build())
                .build();
    }
}
