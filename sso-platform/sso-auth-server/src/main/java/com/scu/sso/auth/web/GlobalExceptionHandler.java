package com.scu.sso.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 接口错误响应统一为错误码 + 对外提示 + 请求标识，不返回堆栈。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApiException(ApiException exception,
                                                                 HttpServletRequest request) {
        return ResponseEntity.status(exception.getStatus()).body(body(exception.getCode(), exception.getMessage(), request));
    }

    private Map<String, Object> body(String code, String message, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        String requestId = request.getHeader("X-Request-Id");
        body.put("requestId", requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId);
        return body;
    }
}
