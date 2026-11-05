package com.scu.sso.appa.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * 统一认证登录成功（I-WEB-03 的返回端）：把 authMode 置为 SSO，并把访问令牌放进服务端会话。
 * 令牌不出浏览器，页面与接口只持有 Cookie（设计 4.6）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SsoLoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final OAuth2AuthorizedClientRepository authorizedClientRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        HttpSession session = request.getSession(true);
        session.setAttribute(AuthModeFilter.AUTH_MODE, AuthModeFilter.MODE_SSO);
        if (authentication instanceof OAuth2AuthenticationToken token) {
            OAuth2AuthorizedClient client = authorizedClientRepository.loadAuthorizedClient(
                    token.getAuthorizedClientRegistrationId(), authentication, request);
            if (client != null && client.getAccessToken() != null) {
                session.setAttribute(AuthModeFilter.ACCESS_TOKEN, client.getAccessToken().getTokenValue());
            } else {
                log.warn("统一认证成功后未取到访问令牌，后续受保护访问将被拒绝");
            }
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
