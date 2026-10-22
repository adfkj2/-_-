package com.scu.sso.auth.repository;

import com.scu.sso.auth.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 审计写入仓储。
 */
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
}
