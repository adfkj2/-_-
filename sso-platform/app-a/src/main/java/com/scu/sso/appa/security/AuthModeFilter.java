package com.scu.sso.appa.security;

import com.scu.sso.appa.client.IntrospectionClient;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 受保护资源访问控制（F03）：本地演示会话一律拒绝，统一认证会话每次请求先内省。
 * 页面拒绝时跳转登录页并提示“该资源需统一认证”，接口拒绝时返回 401 且不含业务数据（设计 4.6、8.5）。
 */
@Component
@RequiredArgsConstructor
public class AuthModeFilter extends OncePerRequestFilter {

    /** 会话属性：登录来源，LOCAL 或 SSO。 */
    public static final String AUTH_MODE = "authMode";
    /** 会话属性：统一认证拿到的访问令牌，只存服务端。 */
    public static final String ACCESS_TOKEN = "accessToken";
    /** 会话属性：本地演示登录的用户名。 */
    public static final String LOCAL_USER = "localUser";

    public static final String MODE_LOCAL = "LOCAL";
    public static final String MODE_SSO = "SSO";

    private final IntrospectionClient introspectionClient;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = path(request);
        return !(path.equals("/") || path.equals("/home") || path.startsWith("/api/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean api = path(request).startsWith("/api/");
        HttpSession session = request.getSession(false);
        String mode = session == null ? null : (String) session.getAttribute(AUTH_MODE);
        if (!MODE_SSO.equals(mode)) {
            // 本地演示会话没有令牌，不能进入受保护资源
            reject(response, request, api, "needSso");
            return;
        }
        String token = (String) session.getAttribute(ACCESS_TOKEN);
        switch (introspectionClient.introspect(token)) {
            case ACTIVE -> chain.doFilter(request, response);
            case INACTIVE -> {
                session.invalidate();
                SecurityContextHolder.clearContext();
                reject(response, request, api, "expired");
            }
            case UNAVAILABLE -> {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                        "{\"code\":\"ERR-AUTH-05\",\"message\":\"认证中心暂时不可用，请稍后重试\"}");
            }
        }
    }

    private void reject(HttpServletResponse response, HttpServletRequest request, boolean api, String reason)
            throws IOException {
        if (api) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"code\":\"ERR-AUTH-04\",\"message\":\"该资源需统一认证，请使用统一认证登录\"}");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/login?" + reason + "=1");
    }

    private String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return context.isEmpty() ? uri : uri.substring(context.length());
    }
}
