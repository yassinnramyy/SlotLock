package com.slotlock.application.exception;

import org.springframework.http.HttpStatus;

public class BusinessLogicViolationException extends ApiException {

    public BusinessLogicViolationException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCodeEnum.BUSINESS_RULE_VIOLATION, message);
    }

    public BusinessLogicViolationException(HttpStatus status, ApiErrorCodeEnum errorCode, String message) {
        super(status, errorCode, message);
    }
}
