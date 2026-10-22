package com.scu.sso.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.scu.sso.auth.domain.AppUser;
import com.scu.sso.auth.domain.AuthSession;
import com.scu.sso.auth.repository.AppUserRepository;
import com.scu.sso.auth.session.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * F06：只能查看与结束本人会话，越权一律 403 且不暴露会话是否存在。
 */
class MeSessionControllerTest {

    private static final Long CURRENT_USER_ID = 7L;

    private AppUserRepository userRepository;
    private SessionService sessionService;
    private MeSessionController controller;
    private Authentication authentication;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        userRepository = mock(AppUserRepository.class);
        sessionService = mock(SessionService.class);
        request = mock(HttpServletRequest.class);
        AppUser user = new AppUser();
        user.setId(CURRENT_USER_ID);
        user.setUsername("alice01");
        when(userRepository.findByUsername("alice01")).thenReturn(Optional.of(user));
        when(request.getSession(false)).thenReturn(null);
        controller = new MeSessionController(userRepository, sessionService);
        authentication = new UsernamePasswordAuthenticationToken("alice01", "n/a", List.of());
    }

    private AuthSession session(String sid, Long userId, String status) {
        AuthSession session = new AuthSession();
        session.setSid(sid);
        session.setUserId(userId);
        session.setStatus(status);
        session.setCreatedAt(Instant.parse("2026-10-22T08:00:00Z"));
        session.setLastActiveAt(Instant.parse("2026-10-22T08:10:00Z"));
        session.setAbsoluteExpiresAt(Instant.parse("2026-10-22T16:00:00Z"));
        session.setDeviceSummary("Chrome / 127.0.0.*");
        return session;
    }

    @Test
    @DisplayName("返回本人会话列表")
    void listsOwnSessions() {
        when(sessionService.listOf(CURRENT_USER_ID)).thenReturn(List.of(
                session("sid-1", CURRENT_USER_ID, "ACTIVE"),
                session("sid-2", CURRENT_USER_ID, "REVOKED")));

        List<SessionView> views = controller.list(authentication, request);

        assertThat(views).hasSize(2);
        assertThat(views.get(0).sid()).isEqualTo("sid-1");
        assertThat(views.get(1).status()).isEqualTo("REVOKED");
    }

    @Test
    @DisplayName("结束他人会话抛出 403 与统一错误码")
    void rejectsRevokingOthersSession() {
        when(sessionService.findBySid("sid-9")).thenReturn(Optional.of(session("sid-9", 99L, "ACTIVE")));

        assertThatThrownBy(() -> controller.revoke("sid-9", authentication, request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiException = (ApiException) ex;
                    assertThat(apiException.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiException.getCode()).isEqualTo("ERR-USER-06");
                });
    }
}
