package com.scu.sso.appa.config;

import com.scu.sso.appa.security.AuthModeFilter;
import com.scu.sso.appa.security.SsoLoginSuccessHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import lombok.RequiredArgsConstructor;

/**
 * 应用侧双入口安全配置（设计 4.6）：
 * 本地演示链只覆盖登录页、本地登录接口与本地演示页；其余路径走统一认证链，
 * 并在授权过滤器之后用 AuthModeFilter 做 authMode 判定与实时内省。
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AppSsoProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private final ClientRegistrationRepository clientRegistrationRepository;
    private final SsoLoginSuccessHandler successHandler;
    private final AuthModeFilter authModeFilter;

    /**
     * 本地演示链：不涉及中心，也不加载 OAuth2 登录。
     */
    @Bean
    @Order(1)
    public SecurityFilterChain localPreviewChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/login", "/login/local", "/local-demo", "/css/**", "/favicon.ico")
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login", "/login/local", "/css/**", "/favicon.ico").permitAll()
                        .anyRequest().authenticated())
                .formLogin(AbstractHttpConfigurer::disable)
                .oauth2Login(AbstractHttpConfigurer::disable)
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")));
        return http.build();
    }

    /**
     * 统一认证链：受保护页面与接口、OIDC 回调、退出。
     */
    @Bean
    @Order(2)
    public SecurityFilterChain ssoChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/css/**", "/js/**", "/favicon.ico", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login")
                        .successHandler(successHandler)
                        .authorizationEndpoint(endpoint ->
                                endpoint.authorizationRequestResolver(pkceResolver())))
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true))
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
                .exceptionHandling(exception -> exception
                        .defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                new AntPathRequestMatcher("/api/**"))
                        .defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login"),
                                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                .addFilterAfter(authModeFilter, AuthorizationFilter.class);
        return http.build();
    }

    /**
     * 中心强制 PKCE S256，应用侧必须在授权请求里带 code_challenge。
     */
    private OAuth2AuthorizationRequestResolver pkceResolver() {
        DefaultOAuth2AuthorizationRequestResolver resolver = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository, "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        return resolver;
    }
}
