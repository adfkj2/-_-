package com.scu.sso.auth.web;

import com.scu.sso.auth.domain.AppUser;
import com.scu.sso.auth.domain.AuthSession;
import com.scu.sso.auth.repository.AppUserRepository;
import com.scu.sso.auth.session.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户登录会话管理（F06）：只能查看与结束本人会话。
 */
@RestController
@RequestMapping("/api/me/sessions")
@RequiredArgsConstructor
public class MeSessionController {

    private final AppUserRepository userRepository;
    private final SessionService sessionService;

    @GetMapping
    public List<SessionView> list(Authentication authentication, HttpServletRequest request) {
        Long userId = currentUserId(authentication);
        String currentSid = currentSid(request);
        return sessionService.listOf(userId).stream()
                .map(session -> new SessionView(session.getSid(), session.getStatus(), session.getCreatedAt(),
                        session.getLastActiveAt(), session.getAbsoluteExpiresAt(), session.getDeviceSummary(),
                        session.getSid().equals(currentSid)))
                .toList();
    }

    @DeleteMapping("/{sid}")
    public ResponseEntity<Void> revoke(@PathVariable String sid, Authentication authentication,
                                       HttpServletRequest request) {
        Long userId = currentUserId(authentication);
        AuthSession session = sessionService.findBySid(sid)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ERR-USER-06", "无权操作该会话"));
        if (!session.getUserId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ERR-USER-06", "无权操作该会话");
        }
        sessionService.revoke(session, "SESSION_REVOKE_BY_USER");
        HttpSession httpSession = request.getSession(false);
        if (httpSession != null && sid.equals(httpSession.getId())) {
            httpSession.invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    private Long currentUserId(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .map(AppUser::getId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "ERR-AUTH-04", "登录已失效，请重新登录"));
    }

    private String currentSid(HttpServletRequest request) {
        HttpSession httpSession = request.getSession(false);
        return httpSession == null ? null : httpSession.getId();
    }
}
