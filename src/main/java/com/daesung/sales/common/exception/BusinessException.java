package com.daesung.sales.common.exception;

import lombok.Getter;

/** 도메인 비즈니스 예외. ErrorCode를 담아 GlobalExceptionHandler가 응답으로 변환한다. */
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
