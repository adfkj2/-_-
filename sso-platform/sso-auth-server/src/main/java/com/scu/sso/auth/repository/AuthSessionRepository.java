package com.scu.sso.auth.repository;

import com.scu.sso.auth.domain.AuthSession;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 中心会话仓储。
 */
public interface AuthSessionRepository extends JpaRepository<AuthSession, String> {

    List<AuthSession> findByUserIdOrderByLastActiveAtDesc(Long userId);

    List<AuthSession> findByUserIdAndStatus(Long userId, String status);

    /**
     * 撤销某账号全部有效会话，返回受影响行数。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AuthSession s set s.status = 'REVOKED', s.revokedAt = :at, s.revokeReason = :reason "
            + "where s.userId = :userId and s.status = 'ACTIVE'")
    int revokeAllOfUser(@Param("userId") Long userId, @Param("at") Instant at, @Param("reason") String reason);
}
