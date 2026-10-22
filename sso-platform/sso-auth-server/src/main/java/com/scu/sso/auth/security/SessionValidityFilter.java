package com.scu.sso.auth.security;

import com.scu.sso.auth.domain.AuthSession;
import com.scu.sso.auth.repository.AuthSessionRepository;
import com.scu.sso.auth.session.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 会话有效性过滤器（F05）：每次受保护访问都以服务端记录判定，过期立即失效并撤销。
 * 页面请求跳转登录页，接口请求返回 401。
 */
@Component
@RequiredArgsConstructor
public class SessionValidityFilter extends OncePerRequestFilter {

    private final AuthSessionRepository sessionRepository;
    private final SessionService sessionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            chain.doFilter(request, response);
            return;
        }
        HttpSession httpSession = request.getSession(false);
        if (httpSession == null) {
            chain.doFilter(request, response);
            return;
        }
        AuthSession session = sessionRepository.findById(httpSession.getId()).orElse(null);
        if (session == null) {
            reject(request, response, httpSession);
            return;
        }
        if (!sessionService.isActive(session)) {
            sessionService.expire(session);
            reject(request, response, httpSession);
            return;
        }
        sessionService.touch(session);
        chain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, HttpSession httpSession)
            throws IOException {
        httpSession.invalidate();
        SecurityContextHolder.clearContext();
        if (expectsHtml(request)) {
            response.sendRedirect(request.getContextPath() + "/login?expired");
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"ERR-AUTH-04\",\"message\":\"登录已失效，请重新登录\"}");
    }

    private boolean expectsHtml(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("text/html");
    }
}
