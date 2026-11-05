package com.scu.sso.auth.oauth;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.stereotype.Component;

/**
 * 授权吊销：按中心会话或按用户删除授权记录，使访问令牌立即失效（设计 4.2、BR-19）。
 * 退出、会话到期、用户主动结束会话、账号停用都走这里。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthorizationRevoker {

    private final OAuth2AuthorizationService authorizationService;
    private final AuthorizationLinkRepository linkRepository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 撤销某个中心会话关联的全部授权，返回删除条数。
     */
    public int revokeBySid(String sid) {
        return doRevoke(linkRepository.findAuthorizationIdsBySid(sid));
    }

    /**
     * 撤销某个用户的全部授权（统一退出、停用、改密）。
     */
    public int revokeByUser(Long userId) {
        Set<String> ids = new LinkedHashSet<>(linkRepository.findAuthorizationIdsByUser(userId));
        // 关联表缺失时的兜底：按主体名直接查授权记录
        ids.addAll(jdbcTemplate.queryForList("""
                SELECT a.id FROM oauth2_authorization a
                  JOIN sys_user u ON u.username = a.principal_name
                 WHERE u.id = ?
                """, String.class, userId));
        return doRevoke(ids);
    }

    private int doRevoke(Collection<String> authorizationIds) {
        int revoked = 0;
        for (String id : authorizationIds) {
            OAuth2Authorization authorization = authorizationService.findById(id);
            if (authorization == null) {
                linkRepository.delete(id);
                continue;
            }
            authorizationService.remove(authorization);
            revoked++;
        }
        log.debug("撤销授权 count={}", revoked);
        return revoked;
    }
}
