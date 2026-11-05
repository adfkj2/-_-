package com.scu.sso.auth.audit;

import com.scu.sso.auth.domain.AuditEvent;
import com.scu.sso.auth.repository.AuditEventRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审计写入。审计查询与统计属于 F15/F16，本类只负责产生记录。
 * event_key 由“事件类型 + 结果 + 主体 + requestId + 摘要”生成，重试同一请求不会重复入库（设计 4.5）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditWriter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    private final AuditEventRepository repository;

    /**
     * 写入一条审计记录。审计失败不影响主流程，只记录日志。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String eventType, String result, Long actorUserId, HttpServletRequest request, String detail) {
        try {
            String requestId = requireRequestId(request);
            AuditEvent event = new AuditEvent();
            event.setEventKey(eventKey(eventType, result, actorUserId, requestId, detail));
            event.setActorUserId(actorUserId);
            event.setEventType(eventType);
            event.setResult(result);
            event.setOccurredAt(Instant.now());
            event.setRequestId(requestId);
            event.setSourceMasked(maskAddress(request == null ? null : request.getRemoteAddr()));
            event.setDetailJson(detail == null ? null : "{\"note\":\"" + detail.replace("\"", "") + "\"}");
            repository.saveAndFlush(event);
        } catch (DataIntegrityViolationException duplicate) {
            log.debug("审计事件已存在，忽略重复写入 type={} result={}", eventType, result);
        } catch (RuntimeException ex) {
            log.warn("审计写入失败 type={} result={}", eventType, result, ex);
        }
    }

    private String eventKey(String eventType, String result, Long actorUserId, String requestId, String detail) {
        String raw = eventType + "|" + result + "|" + (actorUserId == null ? "-" : actorUserId) + "|"
                + requestId + "|" + (detail == null ? "" : detail);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("无法计算审计幂等键", ex);
        }
    }

    private String requireRequestId(HttpServletRequest request) {
        if (request != null) {
            String header = request.getHeader(REQUEST_ID_HEADER);
            if (header != null && !header.isBlank()) {
                return header.length() > 64 ? header.substring(0, 64) : header;
            }
        }
        return UUID.randomUUID().toString();
    }

    private String maskAddress(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        int lastDot = address.lastIndexOf('.');
        return lastDot > 0 ? address.substring(0, lastDot) + ".*" : address;
    }
}
