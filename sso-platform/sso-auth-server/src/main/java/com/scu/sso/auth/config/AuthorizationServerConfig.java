package com.scu.sso.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.scu.sso.auth.audit.AuditWriter;
import com.scu.sso.auth.oauth.AuditingAuthorizationService;
import com.scu.sso.auth.oauth.AuthorizationLinkRepository;
import com.scu.sso.auth.repository.AppUserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2RefreshTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

/**
 * OAuth2 授权服务器配置：授权码 + PKCE S256，令牌有效期 10 分钟，不签发刷新令牌。
 * 客户端在演示环境按公共客户端处理（无共享密钥），接入管理属于 F13，暂用内存仓库。
 */
@Configuration
public class AuthorizationServerConfig {

    /**
     * 协议端点安全链，优先级高于页面链。
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServer = OAuth2AuthorizationServerConfigurer.authorizationServer();
        http.securityMatcher(authorizationServer.getEndpointsMatcher())
                .with(authorizationServer, server -> server.oidc(Customizer.withDefaults()))
                .csrf(csrf -> csrf.ignoringRequestMatchers(authorizationServer.getEndpointsMatcher()))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .exceptionHandling(exception -> exception.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        htmlRequestMatcher()));
        return http.build();
    }

    /**
     * 只有真正接受 text/html 的请求才跳登录页；接口调用返回 401。
     */
    private MediaTypeRequestMatcher htmlRequestMatcher() {
        MediaTypeRequestMatcher matcher = new MediaTypeRequestMatcher(MediaType.TEXT_HTML);
        matcher.setIgnoredMediaTypes(Set.of(MediaType.ALL));
        return matcher;
    }

    /**
     * 演示用客户端：两个业务系统各一个，机密客户端，回调地址必须精确匹配。
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository(
            @Value("${sso.clients.app-a.secret}") String appASecret,
            @Value("${sso.clients.app-b.secret}") String appBSecret,
            PasswordEncoder passwordEncoder) {
        return new InMemoryRegisteredClientRepository(
                RegisteredClientFactory.build("app-a", "教务信息服务 A",
                        "http://127.0.0.1:8081/login/oauth2/code/sso", appASecret, passwordEncoder),
                RegisteredClientFactory.build("app-b", "图书信息服务 B",
                        "http://127.0.0.1:8082/login/oauth2/code/sso", appBSecret, passwordEncoder));
    }

    /**
     * 签名密钥。演示环境每次启动重新生成，重启后旧令牌自然失效。
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        KeyPair keyPair = generateRsaKey();
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID(UUID.randomUUID().toString())
                .build();
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    private static KeyPair generateRsaKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("无法生成 RSA 密钥", ex);
        }
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    public OAuth2TokenGenerator<?> tokenGenerator(JwtEncoder jwtEncoder) {
        return new DelegatingOAuth2TokenGenerator(
                new JwtGenerator(jwtEncoder),
                new OAuth2AccessTokenGenerator(),
                new OAuth2RefreshTokenGenerator());
    }

    /**
     * 授权记录落库（设计 5.9）：客户端仓库仍用内存实现（应用接入属 F13），
     * 但授权必须持久化，否则退出、到期、撤权无法吊销令牌。
     */
    @Bean
    public OAuth2AuthorizationService authorizationService(JdbcTemplate jdbcTemplate,
                                                          RegisteredClientRepository registeredClientRepository,
                                                          AuthorizationLinkRepository linkRepository,
                                                          AppUserRepository userRepository,
                                                          AuditWriter auditWriter) {
        return new AuditingAuthorizationService(
                new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository),
                linkRepository, userRepository, auditWriter);
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings(@Value("${sso.issuer}") String issuer) {
        return AuthorizationServerSettings.builder().issuer(issuer).build();
    }
}
