package com.scu.sso.auth.config;

import com.scu.sso.auth.security.SessionValidityFilter;
import com.scu.sso.auth.security.SsoAuthenticationSuccessHandler;
import com.scu.sso.auth.security.UnifiedLoginFailureHandler;
import com.scu.sso.auth.session.CenterSessionLogoutHandler;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

/**
 * 页面与接口安全链：表单登录、统一失败提示、中心会话退出、CSRF 防护。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 密码散列使用 BCrypt，与库中 password_hash 字段一致。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 该过滤器只在安全链中执行，关闭容器级自动注册，避免重复执行。
     */
    @Bean
    public FilterRegistrationBean<SessionValidityFilter> sessionValidityFilterRegistration(SessionValidityFilter filter) {
        FilterRegistrationBean<SessionValidityFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @Order(2)
    public SecurityFilterChain applicationSecurityFilterChain(
            HttpSecurity http,
            SsoAuthenticationSuccessHandler successHandler,
            UnifiedLoginFailureHandler failureHandler,
            CenterSessionLogoutHandler logoutHandler,
            SessionValidityFilter sessionValidityFilter) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login", "/css/**", "/js/**", "/favicon.ico", "/error").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler(successHandler)
                        .failureHandler(failureHandler))
                .logout(logout -> logout
                        .logoutRequestMatcher(new AntPathRequestMatcher("/logout", "POST"))
                        .addLogoutHandler(logoutHandler)
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true))
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
                .exceptionHandling(exception -> exception
                        .defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                new AntPathRequestMatcher("/api/**"))
                        .defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login"),
                                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .addFilterAfter(sessionValidityFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
