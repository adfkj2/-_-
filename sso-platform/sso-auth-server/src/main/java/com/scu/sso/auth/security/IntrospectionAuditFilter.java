package com.scu.sso.auth.security;

import com.scu.sso.auth.audit.AuditWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * 令牌内省审计（F03）：应用每次受保护请求都会调用 /oauth2/introspect，
 * 这里按响应里的 active 结果记一条 TOKEN_INTROSPECT，供验收演示按 requestId 串起链路。
 * 只记录结果与请求标识，不落令牌内容。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
@RequiredArgsConstructor
public class IntrospectionAuditFilter extends OncePerRequestFilter {

    private final AuditWriter auditWriter;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/oauth2/introspect");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(request, wrapper);
            String body = new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
            boolean active = body.contains("\"active\":true");
            auditWriter.record("TOKEN_INTROSPECT", active ? "SUCCESS" : "FAILURE", null, request,
                    "active=" + active);
        } finally {
            wrapper.copyBodyToResponse();
        }
    }
}
