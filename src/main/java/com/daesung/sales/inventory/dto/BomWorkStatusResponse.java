package com.daesung.sales.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 세트 <b>조립·해체 현황</b>(30p). 근거: 발주처 화면검토(2026-08-31) 화면30 —
 * "화면 27(물류 작업비 계산)의 비용 산출과는 <b>연동되지 않도록 분리</b>해,
 * <b>세트 조립·해체 작업을 진행한 현황(결과)만</b> 보여주는 조회 화면으로 유지".
 *
 * <p>★<b>비용이 없다.</b> 작업비 관련 필드를 한 칸도 두지 않는다 —
 * 발주처가 요구한 '분리'는 화면을 나누는 것이 아니라 <b>이 화면이 비용을 말하지 않는 것</b>이다.
 * 금액 칸이 하나라도 있으면 화면27과 값이 갈리는 순간 어느 쪽이 맞는지 다투게 된다.
 * (조립 작업비 자동계산은 2026-08-21 폐기 확정 — V47에서 제거됨.)
 */
@Schema(description = "세트 조립·해체 현황(30p). 수량만 — 작업비는 화면27 소관")
public record BomWorkStatusResponse(

        @Schema(description = "세트(완제품)별 작업 현황") List<Row> rows,

        @Schema(description = """
                이 기간 조립·해체로 움직인 구성품 합계.
                ⚠️**어느 세트 작업에 쓰였는지는 나누지 않는다** — 원장에 완제품↔구성품 링크가 없다.
                BOM 비율로 역산하면 그럴듯한 숫자가 나오지만, 작업 뒤 BOM이 바뀌었으면
                실제와 다른 값을 사실처럼 보여주게 된다.""")
        List<Component> components,

        @Schema(description = "조립 작업 건수 합계") long totalAssembleCount,
        @Schema(description = "조립 세트 수량 합계") long totalAssembleQty,
        @Schema(description = "해체 작업 건수 합계") long totalDisassembleCount,
        @Schema(description = "해체 세트 수량 합계") long totalDisassembleQty
) {

    /** 세트(완제품) 한 줄. 작업 건수는 완제품 이벤트 수 — 작업 1회에 정확히 하나 생긴다. */
    @Schema(name = "BomWorkStatusRow")
    public record Row(
            @Schema(description = "분류코드") String catCode,
            @Schema(description = "분류명") String catName,
            @Schema(description = "세트 도서 id") Long productId,
            @Schema(description = "세트 도서코드") String productCode,
            @Schema(description = "세트 도서명") String productName,
            @Schema(description = "조립 작업 건수") long assembleCount,
            @Schema(description = "조립한 세트 수량") long assembleQty,
            @Schema(description = "해체 작업 건수") long disassembleCount,
            @Schema(description = "해체한 세트 수량") long disassembleQty,
            @Schema(description = "순증 = 조립 − 해체. 음수면 이 기간에 푼 쪽이 많다") long netQty,
            @Schema(description = "최근 작업일") LocalDate lastWorkedAt
    ) {
    }

    /** 구성품 한 줄(기간 합계). 세트 귀속은 없다 — 위 {@code components} 설명 참고. */
    @Schema(name = "BomWorkComponent")
    public record Component(
            @Schema(description = "구성품 id") Long productId,
            @Schema(description = "구성품 코드") String productCode,
            @Schema(description = "구성품명") String productName,
            @Schema(description = "조립으로 **소모된** 수량") long usedQty,
            @Schema(description = "해체로 **되돌아온** 수량") long restoredQty,
            @Schema(description = "순소모 = 소모 − 복원") long netUsedQty
    ) {
    }
}
