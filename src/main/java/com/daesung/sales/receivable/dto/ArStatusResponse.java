package com.daesung.sales.receivable.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/** 미수금(외상매출) 현황. 거래처별 잔액 + 담보. 근거: 레거시 외상매출현황조회. */
public record ArStatusResponse(
        @Schema(description = "집계 시작일") LocalDate fromDate,
        @Schema(description = "집계 종료일") LocalDate toDate,
        @Schema(description = "거래처별 채권 현황") List<Row> rows,
        @Schema(description = "합계행") Row total
) {
    @Schema(name = "ArStatusRow")
    public record Row(
            @Schema(description = "거래처 id(합계행 null)") Long partnerId,
            @Schema(description = "거래처코드") String partnerCode,
            @Schema(description = "거래처명") String partnerName,
            @Schema(description = "이월(전년말 잔액 스냅샷)") long opening,
            @Schema(description = "기간 매출액") long saleAmount,
            @Schema(description = "기간 반품액") long returnAmount,
            @Schema(description = "기간 세액(순)") long tax,
            @Schema(description = "기간 채권발생(매출+세액−반품)") long receivableGen,
            @Schema(description = "기간 수금") long collected,
            @Schema(description = "잔액(이월+채권발생−수금)") long balance,
            @Schema(description = """
                    교사용(증정 포함) 공급가액. ‼️무가라 **채권에는 들어가지 않는다** —
                    잔액·채권발생과 더하지 말 것. "이 거래처에 무가로 얼마가 나갔나"를 보는 칸이다.""")
            long teacherAmount,

            @Schema(description = """
                    교사용(증정 포함) 수량. ‼️금액과 마찬가지로 **채권에는 들어가지 않는다** —
                    잔액·채권발생과 더하지 말 것.""")
            long teacherQty,
            @Schema(description = "담보금액(여신한도)") Long assureAmount,
            @Schema(description = "담보비율(%)=잔액/담보×100, 담보 없으면 null") Double assureRatio,
            @Schema(description = "담보 만기") LocalDate assureExpiry,
            @Schema(description = "담보 내용(정본 25p 데이터 항목 '담보(금액,만기,내용)')") String assureNote,
            @Schema(description = "담보 경고등급(OVER≥100/WARN≥70/WATCH>50/NORMAL/null)") String assureLevel
    ) {
    }
}
