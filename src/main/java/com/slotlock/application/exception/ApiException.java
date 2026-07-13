package com.slotlock.application.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ApiErrorCodeEnum errorCode;

    public ApiException(HttpStatus status, ApiErrorCodeEnum errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ApiErrorCodeEnum getErrorCode() {
        return errorCode;
    }
}
