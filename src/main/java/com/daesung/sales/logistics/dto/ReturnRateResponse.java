package com.daesung.sales.logistics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 회수단가(반품·사고 회수 시 적용) 현재값. DSRE2 {@code tbl_logis_cost} 의 특수행(dtl_cd=0).
 *
 * <p>★<b>저장은 되는데 읽을 수가 없었다.</b> PUT만 있고 GET이 없어, 화면의 회수단가 입력창이
 * 빈 칸으로 열렸다(프론트 실호출 실측 §P-2). 지금 값을 모르고 덮어쓰면
 * 담당자가 바꾸려던 항목 말고 다른 항목까지 0으로 밀린다.
 *
 * <p>필드명 {@code book}은 화면 표기(단행본)를 따랐다 — DSRE 원본 컬럼명은 ETC다.
 */
public record ReturnRateResponse(

        @Schema(description = "시험지(OMR 외) 단가") int paper,
        @Schema(description = "OMR 단가") int omr,
        @Schema(description = "단행본 단가(DSRE 원본 컬럼 ETC)") int book,
        @Schema(description = "마지막 변경일시. 회수단가 행이 아직 없으면 null") LocalDateTime changedAt
) {
}
