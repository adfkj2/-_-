package com.scu.sso.appa.client;

import com.scu.sso.appa.config.AppSsoProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 令牌内省客户端（F03）：每次受保护请求都调用中心 /oauth2/introspect，不做正向缓存。
 * 中心不可达或返回异常时按拒绝处理（fail closed），返回 503 而不是放行。
 */
@Slf4j
@Component
public class IntrospectionClient {

    /** 内省结论。 */
    public enum Outcome {
        /** 令牌有效且授权仍在。 */
        ACTIVE,
        /** 令牌无效、已过期或授权已撤销。 */
        INACTIVE,
        /** 中心不可达或拒绝调用，无法判定。 */
        UNAVAILABLE
    }

    private final AppSsoProperties properties;
    private final RestClient restClient;

    public IntrospectionClient(AppSsoProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.introspection().connectTimeoutMs());
        factory.setReadTimeout(properties.introspection().readTimeoutMs());
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /**
     * 判定服务端持有的访问令牌是否仍然有效。
     */
    public Outcome introspect(String token) {
        if (token == null || token.isBlank()) {
            return Outcome.INACTIVE;
        }
        try {
            Map<String, Object> body = restClient.post()
                    .uri(properties.introspection().uri())
                    .headers(headers -> {
                        headers.setBasicAuth(properties.appId(), properties.appSecret());
                        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
                        headers.set("X-Request-Id", UUID.randomUUID().toString());
                    })
                    .body("token=" + URLEncoder.encode(token, StandardCharsets.UTF_8))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            boolean active = body != null && Boolean.TRUE.equals(body.get("active"));
            return active ? Outcome.ACTIVE : Outcome.INACTIVE;
        } catch (RuntimeException ex) {
            log.warn("内省调用失败，按拒绝处理：{}", ex.getMessage());
            return Outcome.UNAVAILABLE;
        }
    }
}
