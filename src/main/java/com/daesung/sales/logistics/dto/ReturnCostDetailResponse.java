package com.daesung.sales.logistics.dto;

import com.daesung.sales.dsre.gateway.LogisMode;
import com.daesung.sales.dsre.gateway.ReturnCostDetailRow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 회수 물류비 명세 + 소계(자재구분별·구분별) + 총계. */
@Schema(name = "ReturnCostDetailResponse", description = "회수 작업비 명세")
public record ReturnCostDetailResponse(
        @Schema(description = "시작일") LocalDate fromDate,
        @Schema(description = "종료일") LocalDate toDate,
        @Schema(description = "구분 ALL/NORMAL/ACCIDENT") LogisMode mode,
        @Schema(description = "행 명세") List<ReturnCostDetailRow> rows,

        @Schema(description = """
                자재구분별 소계. ★합은 `GET /logistics-costs/return` 총계와 같다 —
                같은 단가 규칙으로 계산하기 때문이다.""")
        List<Subtotal> byType,

        @Schema(description = "구분(사고/반품)별 소계") List<Subtotal> byMode,
        @Schema(description = "총 수량") long totalQty,
        @Schema(description = "총 금액") long totalAmount
) {
    @Schema(name = "ReturnCostSubtotal")
    public record Subtotal(
            @Schema(description = "구분 값") String key,
            @Schema(description = "수량 합") long qty,
            @Schema(description = "금액 합") long amount
    ) {
    }

    public static ReturnCostDetailResponse of(LocalDate from, LocalDate to, LogisMode mode,
                                              List<ReturnCostDetailRow> rows) {
        Map<String, long[]> byType = new LinkedHashMap<>();
        Map<String, long[]> byMode = new LinkedHashMap<>();
        long qty = 0;
        long amt = 0;
        for (ReturnCostDetailRow r : rows) {
            accumulate(byType, r.materialType(), r.qty(), r.amount());
            accumulate(byMode, r.mode(), r.qty(), r.amount());
            qty += r.qty();
            amt += r.amount();
        }
        return new ReturnCostDetailResponse(from, to, mode, rows,
                toList(byType), toList(byMode), qty, amt);
    }

    private static void accumulate(Map<String, long[]> acc, String key, long qty, long amount) {
        long[] v = acc.computeIfAbsent((key == null) ? "미지정" : key, k -> new long[2]);
        v[0] += qty;
        v[1] += amount;
    }

    private static List<Subtotal> toList(Map<String, long[]> acc) {
        List<Subtotal> out = new ArrayList<>();
        acc.forEach((k, v) -> out.add(new Subtotal(k, v[0], v[1])));
        return out;
    }
}
