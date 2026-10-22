package com.scu.sso.auth.security;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 读取用户角色编码。角色维护属于 F12，本类只读。
 */
@Repository
@RequiredArgsConstructor
public class UserRoleReader {

    private final JdbcTemplate jdbcTemplate;

    public List<String> findRoleCodes(Long userId) {
        return jdbcTemplate.queryForList(
                "select r.code from sys_role r join user_role ur on ur.role_id = r.id "
                        + "where ur.user_id = ? and r.enabled = 1 order by r.code",
                String.class, userId);
    }
}
