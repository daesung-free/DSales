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
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),               // 권한 부족(403)
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 메서드입니다."),  // 경로는 맞고 메서드만 틀린 호출
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 Content-Type입니다."),  // 업로드에 JSON을 보낸 경우 등
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    // --- 도메인(시트 근거) ---
    PERIOD_LOCKED(HttpStatus.CONFLICT, "마감된 월입니다. 수정할 수 없습니다."),   // 월마감(4.9)
    // ⚠️아래 둘은 **더 이상 던지지 않는다**(발주처 2026-08-31 회신으로 차단을 걷어냈다).
    //   재고 음수·반품 초과는 이제 막지 않고 **경고**로 알린다 —
    //   음수재고: InventoryService.applyDelta 의 log.warn
    //   반품 초과: SalesEntryResponse.warnings (코드 문자열 "RETURN_EXCEEDS")
    //   값을 지우지 않고 남겨 둔 것은 이력 때문이다. ‼️다시 throw 하지 말 것 —
    //   막는 순간 발주처가 명시적으로 요구한 동작이 되돌아간다.
    NEGATIVE_STOCK(HttpStatus.CONFLICT, "재고가 음수가 될 수 없습니다."),          // ✝️미사용
    RETURN_EXCEEDS(HttpStatus.CONFLICT, "반품 수량이 출고 잔여를 초과했습니다."),   // ✝️미사용

    OVER_SETTLEMENT(HttpStatus.CONFLICT, "정산 수량이 미결 잔여를 초과했습니다.");  // 정산초안 검증에서 사용

    private final HttpStatus status;
    private final String message;
}
