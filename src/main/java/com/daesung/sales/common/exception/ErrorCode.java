package com.daesung.sales.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 도메인/공통 에러코드. 코드=enum명, message=기본 안내문. */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // --- 공통 ---
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증에 실패했습니다."),          // 로그인/토큰 실패
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    // --- 도메인(시트 근거) ---
    PERIOD_LOCKED(HttpStatus.CONFLICT, "마감된 월입니다. 수정할 수 없습니다."),   // 월마감(4.9)
    NEGATIVE_STOCK(HttpStatus.CONFLICT, "재고가 음수가 될 수 없습니다."),          // 로직A 음수방지
    OVER_SETTLEMENT(HttpStatus.CONFLICT, "정산 수량이 미결 잔여를 초과했습니다.");  // 로직B 초과정산 방지

    private final HttpStatus status;
    private final String message;
}
