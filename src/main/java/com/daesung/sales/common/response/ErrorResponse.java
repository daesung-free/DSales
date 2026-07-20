package com.daesung.sales.common.response;

import com.daesung.sales.common.exception.ErrorCode;
import lombok.Getter;

/** 실패 응답 바디. code=에러코드(enum명), message=사용자 안내 메시지. */
@Getter
public class ErrorResponse {

    private final String code;
    private final String message;

    private ErrorResponse(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.name(), message);
    }
}
