package com.scu.sso.auth.web;

import org.springframework.http.HttpStatus;

/**
 * 接口业务异常，携带错误码与 HTTP 状态，对外提示不暴露内部细节。
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
