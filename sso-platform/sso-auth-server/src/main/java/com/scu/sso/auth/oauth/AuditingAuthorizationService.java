package com.scu.sso.auth.oauth;

import com.scu.sso.auth.audit.AuditWriter;
import com.scu.sso.auth.domain.AppUser;
import com.scu.sso.auth.repository.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 授权记录装饰器：在官方 JDBC 实现之上补两件事。
 * 1. 授权码签发时把当前中心会话 sid 记入授权属性并写入关联表，退出与撤权可按 sid 反查；
 * 2. 首次签发时写 SSO_AUTHORIZE 审计（F02）。
 */
@Slf4j
@RequiredArgsConstructor
public class AuditingAuthorizationService implements OAuth2AuthorizationService {

    /** 授权属性中的会话标识键，与 JdbcOAuth2AuthorizationService 的属性序列化兼容。 */
    public static final String SID_ATTRIBUTE = "sid";

    private final OAuth2AuthorizationService delegate;
    private final AuthorizationLinkRepository linkRepository;
    private final AppUserRepository userRepository;
    private final AuditWriter auditWriter;

    @Override
    public void save(OAuth2Authorization authorization) {
        OAuth2Authorization target = authorization;
        if (authorization.getAttribute(SID_ATTRIBUTE) == null) {
            HttpServletRequest request = currentRequest();
            HttpSession session = request == null ? null : request.getSession(false);
            if (session != null) {
                String sid = session.getId();
                target = OAuth2Authorization.from(authorization).attribute(SID_ATTRIBUTE, sid).build();
                Long userId = userRepository.findByUsername(authorization.getPrincipalName())
                        .map(AppUser::getId)
                        .orElse(null);
                if (userId != null) {
                    linkRepository.link(authorization.getId(), sid, userId);
                }
                if (authorization.getToken(OAuth2AuthorizationCode.class) != null) {
                    auditWriter.record("SSO_AUTHORIZE", "SUCCESS", userId, request,
                            "client=" + authorization.getRegisteredClientId() + ";sid=" + sid);
                }
            }
        }
        delegate.save(target);
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        linkRepository.delete(authorization.getId());
        delegate.remove(authorization);
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return delegate.findById(id);
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        return delegate.findByToken(token, tokenType);
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
