package com.scu.sso.auth.session;

import com.scu.sso.auth.audit.AuditWriter;
import com.scu.sso.auth.domain.AppUser;
import com.scu.sso.auth.domain.AuthSession;
import com.scu.sso.auth.domain.SecurityPolicy;
import com.scu.sso.auth.oauth.AuthorizationRevoker;
import com.scu.sso.auth.repository.AuthSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 中心会话服务：创建、活跃刷新、过期与撤销，对应 F05 与 F06。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private static final Duration TOUCH_INTERVAL = Duration.ofSeconds(30);

    private final AuthSessionRepository repository;
    private final PolicyService policyService;
    private final AuthorizationRevoker authorizationRevoker;
    private final AuditWriter auditWriter;

    /**
     * 登录成功后建立中心会话，写入时限快照与设备摘要。
     */
    @Transactional
    public AuthSession create(AppUser user, HttpServletRequest request, Instant now) {
        SecurityPolicy policy = policyService.current();
        HttpSession httpSession = request.getSession(true);
        AuthSession session = new AuthSession();
        session.setSid(httpSession.getId());
        session.setUserId(user.getId());
        session.setHttpSessionDigest(sha256(httpSession.getId()));
        session.setCreatedAt(now);
        session.setLastActiveAt(now);
        session.setAbsoluteExpiresAt(now.plus(Duration.ofHours(policy.getAbsoluteTimeoutHours())));
        session.setIdleTimeoutSeconds(policy.getIdleTimeoutMinutes() * 60);
        session.setStatus(SessionStatus.ACTIVE);
        session.setDeviceSummary(deviceSummary(request));
        session.setPolicyVersion(policy.getVersion());
        session.setUserAuthVersion(user.getAuthVersion());
        return repository.save(session);
    }

    /**
     * 判定会话当前是否仍然有效。
     */
    public boolean isActive(AuthSession session) {
        if (!SessionStatus.ACTIVE.equals(session.getStatus())) {
            return false;
        }
        return !SessionPolicy.isExpired(Instant.now(), session.getLastActiveAt(),
                session.getIdleTimeoutSeconds(), session.getAbsoluteExpiresAt());
    }

    /**
     * 刷新最近活动时间。为避免每次请求都写库，间隔小于 30 秒时跳过。
     */
    @Transactional
    public void touch(AuthSession session) {
        Instant now = Instant.now();
        if (now.isBefore(session.getLastActiveAt().plus(TOUCH_INTERVAL))) {
            return;
        }
        session.setLastActiveAt(now);
        repository.save(session);
    }

    /**
     * 标记会话过期（时限到达），并撤销其关联授权。
     */
    @Transactional
    public void expire(AuthSession session) {
        terminate(session, SessionStatus.EXPIRED, "IDLE_OR_ABSOLUTE_TIMEOUT");
    }

    /**
     * 撤销会话（退出、改密、停用、用户手动结束）。
     */
    @Transactional
    public void revoke(AuthSession session, String reason) {
        terminate(session, SessionStatus.REVOKED, reason);
    }

    private void terminate(AuthSession session, String status, String reason) {
        if (!SessionStatus.ACTIVE.equals(session.getStatus())) {
            return;
        }
        session.setStatus(status);
        session.setRevokedAt(Instant.now());
        session.setRevokeReason(reason);
        repository.save(session);
        // 会话终止必须同时吊销该会话的授权，否则应用仍能用旧令牌读到受保护数据（设计 4.2）
        authorizationRevoker.revokeBySid(session.getSid());
        if (SessionStatus.EXPIRED.equals(status)) {
            auditWriter.record("SESSION_EXPIRED", "SUCCESS", session.getUserId(), null,
                    "sid=" + session.getSid() + ";reason=" + reason);
        } else if (!reason.startsWith("LOGOUT_")) {
            auditWriter.record("SESSION_REVOKE_BY_USER", "SUCCESS", session.getUserId(), null,
                    "sid=" + session.getSid() + ";reason=" + reason);
        }
        log.debug("会话终止 sid={} status={} reason={}", session.getSid(), status, reason);
    }

    /**
     * 撤销某账号全部有效会话（改密、停用、统一退出全部设备），并吊销其全部授权。
     */
    @Transactional
    public int revokeAllOfUser(Long userId, String reason) {
        int revoked = repository.revokeAllOfUser(userId, Instant.now(), reason);
        authorizationRevoker.revokeByUser(userId);
        return revoked;
    }

    public List<AuthSession> listOf(Long userId) {
        return repository.findByUserIdOrderByLastActiveAtDesc(userId);
    }

    public Optional<AuthSession> findBySid(String sid) {
        return repository.findById(sid);
    }

    /**
     * 设备摘要：浏览器大类 + 脱敏来源地址，不含完整 UA。
     */
    private String deviceSummary(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        String browser = "未知浏览器";
        if (userAgent != null) {
            if (userAgent.contains("Edg/")) {
                browser = "Edge";
            } else if (userAgent.contains("Chrome/")) {
                browser = "Chrome";
            } else if (userAgent.contains("Firefox/")) {
                browser = "Firefox";
            } else if (userAgent.contains("Safari/")) {
                browser = "Safari";
            }
        }
        return browser + " / " + maskAddress(request.getRemoteAddr());
    }

    private String maskAddress(String address) {
        if (address == null || address.isBlank()) {
            return "unknown";
        }
        int lastDot = address.lastIndexOf('.');
        return lastDot > 0 ? address.substring(0, lastDot) + ".*" : address;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("无法计算会话摘要", ex);
        }
    }
}
