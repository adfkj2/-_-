package com.scu.sso.auth.oauth;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 授权记录与中心会话的关联表读写：退出、到期、撤权时按 sid 或用户反查授权（设计 4.2）。
 */
@Repository
@RequiredArgsConstructor
public class AuthorizationLinkRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 建立或更新关联，重复签发同一授权时只保留一条。
     */
    public void link(String authorizationId, String sid, Long userId) {
        jdbcTemplate.update("""
                        INSERT INTO authorization_session_link (authorization_id, sid, user_id, created_at)
                        VALUES (?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE sid = VALUES(sid), user_id = VALUES(user_id)
                        """,
                authorizationId, sid, userId, Timestamp.from(Instant.now()));
    }

    public List<String> findAuthorizationIdsBySid(String sid) {
        return jdbcTemplate.queryForList(
                "SELECT authorization_id FROM authorization_session_link WHERE sid = ?", String.class, sid);
    }

    public List<String> findAuthorizationIdsByUser(Long userId) {
        return jdbcTemplate.queryForList(
                "SELECT authorization_id FROM authorization_session_link WHERE user_id = ?", String.class, userId);
    }

    public void delete(String authorizationId) {
        jdbcTemplate.update("DELETE FROM authorization_session_link WHERE authorization_id = ?", authorizationId);
    }

    public void deleteBySid(String sid) {
        jdbcTemplate.update("DELETE FROM authorization_session_link WHERE sid = ?", sid);
    }
}
