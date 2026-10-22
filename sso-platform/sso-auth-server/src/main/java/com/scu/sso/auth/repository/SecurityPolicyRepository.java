package com.scu.sso.auth.repository;

import com.scu.sso.auth.domain.SecurityPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 安全策略仓储，演示环境只有 id = 1 的一行。
 */
public interface SecurityPolicyRepository extends JpaRepository<SecurityPolicy, Long> {
}
