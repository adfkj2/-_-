package com.scu.sso.auth.config;

import com.scu.sso.auth.domain.AppUser;
import com.scu.sso.auth.domain.SecurityPolicy;
import com.scu.sso.auth.repository.AppUserRepository;
import com.scu.sso.auth.repository.SecurityPolicyRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 演示数据初始化。幂等：重复执行不覆盖已修改的密码，也不重复插入角色与账号。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final AppUserRepository userRepository;
    private final SecurityPolicyRepository policyRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${sso.demo.password:StudyPass2026!}")
    private String demoPassword;

    /** 管理员口令与演示账号口令分离，对应测试文档第 3 章的“测试环境专用安全密码”。 */
    @Value("${sso.demo.admin-password:AdminPass2026!}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedRoles();
        seedPolicy();
        long adminRoleId = roleId("ADMIN");
        long userRoleId = roleId("USER");
        seedUser("admin01", "系统管理员", adminRoleId, "ACTIVE", true, adminPassword);
        seedUser("alice01", "李同学", userRoleId, "ACTIVE", false, demoPassword);
        seedUser("aonly01", "仅 A 用户", userRoleId, "ACTIVE", false, demoPassword);
        seedUser("pending01", "待审核用户", null, "PENDING", false, demoPassword);
        seedUser("disabled01", "停用用户", null, "DISABLED", false, demoPassword);
    }

    private void seedRoles() {
        seedRole("ADMIN", "系统管理员", true);
        seedRole("USER", "普通用户", false);
    }

    private void seedRole(String code, String name, boolean systemRole) {
        Long exists = jdbcTemplate.queryForObject("select count(*) from sys_role where code = ?", Long.class, code);
        if (exists != null && exists > 0) {
            return;
        }
        jdbcTemplate.update("insert into sys_role (code, name, enabled, system_role) values (?, ?, 1, ?)",
                code, name, systemRole);
    }

    private void seedPolicy() {
        if (policyRepository.findById(1L).isPresent()) {
            return;
        }
        SecurityPolicy policy = new SecurityPolicy();
        policy.setId(1L);
        policy.setIdleTimeoutMinutes(30);
        policy.setAbsoluteTimeoutHours(8);
        policy.setFailureWindowMinutes(15);
        policy.setFailureThreshold(5);
        policy.setLockMinutes(15);
        policy.setUpdatedAt(Instant.now());
        policy.setVersion(0);
        policyRepository.save(policy);
        log.info("已初始化默认安全策略：空闲 30 分钟、绝对 8 小时");
    }

    private void seedUser(String username, String displayName, Long roleId, String status,
                          boolean mustChangePassword, String rawPassword) {
        if (userRepository.findByUsername(username).isPresent()) {
            return;
        }
        Instant now = Instant.now();
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setDisplayName(displayName);
        user.setStatus(status);
        user.setMustChangePassword(mustChangePassword);
        user.setAuthVersion(0);
        user.setFailedCount(0);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setVersion(0);
        AppUser saved = userRepository.save(user);
        if (roleId != null) {
            Long linked = jdbcTemplate.queryForObject(
                    "select count(*) from user_role where user_id = ? and role_id = ?", Long.class, saved.getId(), roleId);
            if (linked == null || linked == 0) {
                jdbcTemplate.update("insert into user_role (user_id, role_id) values (?, ?)", saved.getId(), roleId);
            }
        }
        log.info("已初始化演示账号 {}", username);
    }

    private long roleId(String code) {
        Long id = jdbcTemplate.queryForObject("select id from sys_role where code = ?", Long.class, code);
        return id == null ? 0L : id;
    }
}
