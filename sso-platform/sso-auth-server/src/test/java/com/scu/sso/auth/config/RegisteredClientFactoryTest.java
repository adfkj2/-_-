package com.scu.sso.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

/**
 * 客户端参数必须与需求 BR-24 和测试文档第 3 章一致，避免实现悄悄漂移。
 */
class RegisteredClientFactoryTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private RegisteredClient client() {
        return RegisteredClientFactory.build("app-a", "教务信息服务 A",
                "http://127.0.0.1:8081/login/oauth2/code/sso", "app-a-secret", encoder);
    }

    @Test
    @DisplayName("按机密客户端登记，密钥以散列保存")
    void registersConfidentialClientWithHashedSecret() {
        RegisteredClient client = client();
        assertThat(client.getClientAuthenticationMethods())
                .containsExactly(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        assertThat(client.getClientSecret()).isNotEqualTo("app-a-secret");
        assertThat(encoder.matches("app-a-secret", client.getClientSecret())).isTrue();
    }

    @Test
    @DisplayName("强制 PKCE，且授权码 60 秒、令牌 10 分钟、不签发刷新令牌")
    void enforcesPkceAndTokenLifetime() {
        RegisteredClient client = client();
        assertThat(client.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(client.getTokenSettings().getAuthorizationCodeTimeToLive()).isEqualTo(Duration.ofSeconds(60));
        assertThat(client.getTokenSettings().getAccessTokenTimeToLive()).isEqualTo(Duration.ofMinutes(10));
        assertThat(client.getAuthorizationGrantTypes())
                .doesNotContain(org.springframework.security.oauth2.core.AuthorizationGrantType.REFRESH_TOKEN);
    }

    @Test
    @DisplayName("回调地址精确登记，不做通配")
    void registersExactRedirectUri() {
        assertThat(client().getRedirectUris())
                .containsExactly("http://127.0.0.1:8081/login/oauth2/code/sso");
    }
}
