package com.slotlock.application.exception;

import org.springframework.http.HttpStatus;

public class SlotConflictException extends ApiException {

    public SlotConflictException(String message) {
        super(HttpStatus.CONFLICT, ApiErrorCodeEnum.SLOT_CONFLICT, message);
    }
}
