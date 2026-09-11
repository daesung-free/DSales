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
    // ⚠️아래 셋은 한때 **차단 → 경고**로 걷어냈던 것들이다(발주처 2026-08-31 회신).
    //   지금 상태가 서로 다르니 섞지 말 것 —
    //
    //   ★NEGATIVE_STOCK  **다시 던진다**(개발팀 점검 2026-09-11 지적 → 차단 복원).
    //                    InventoryService.applyDelta. 설정으로 뒤집을 수 있다
    //                    (daesung.inventory.allow-negative-stock=true → 종전대로 허용).
    //   RETURN_EXCEEDS   여전히 경고. SalesEntryResponse.warnings ("RETURN_EXCEEDS")
    //   OVER_SETTLEMENT  여전히 경고. SettlementDraftResponse.warnings ("OVER_SETTLEMENT")
    //                    ‼️V56에서 DB 제약·엔티티는 걷어냈는데 정산초안 경로에 차단이
    //                    남아 있었다(2026-09-11 제거). 다시 throw 하지 말 것.
    NEGATIVE_STOCK(HttpStatus.CONFLICT, "재고가 부족합니다."),
    RETURN_EXCEEDS(HttpStatus.CONFLICT, "반품 수량이 출고 잔여를 초과했습니다."),   // ✝️경고로만
    OVER_SETTLEMENT(HttpStatus.CONFLICT, "정산 수량이 미결 잔여를 초과했습니다.");  // ✝️경고로만

    private final HttpStatus status;
    private final String message;
}
