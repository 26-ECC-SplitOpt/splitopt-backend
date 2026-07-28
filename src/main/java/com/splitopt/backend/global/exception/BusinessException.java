package com.splitopt.backend.global.exception;

import lombok.Getter;

/**
 * 비즈니스 로직에서 던지는 공통 예외.
 * 예) {@code throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND);}
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
