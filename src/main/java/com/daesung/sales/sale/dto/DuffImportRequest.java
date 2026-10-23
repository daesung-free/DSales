package com.daesung.sales.sale.dto;

import com.daesung.sales.dsre.gateway.DuffChargeMode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 매출일괄등록(더프) 실행 요청 — <b>레거시 방식</b>(목록을 불러와 사람이 고르고 고쳐서 등록).
 *
 * <p>근거: 발주처 확정(§1) "매출일괄등록은 기존 그대로 유지 — 레거시 그대로 재현, 임의 재설계 금지"
 * + 프론트 요청(2026-10-22 3-2).
 * 레거시 {@code 매출가져오기.vb} 는 ①기간으로 목록을 뽑고 ②담당자가 줄을 체크하고
 * ③<b>분류·도서를 콤보박스에서 골라</b> 체크한 줄 전부에 적용한 뒤 ④값을 고쳐 등록한다.
 *
 * <p>‼️예전엔 기간만 받아 <b>전부 자동 매핑해 등록</b>했다. 그래서
 * ·체크한 줄만 넣을 수 없고 ·상품을 고를 수 없어 '상품 없음'으로 빠지는 줄을 살릴 수 없었다.
 *
 * <p>★모든 칸이 선택이다. 아무것도 안 보내면 예전과 같이 동작한다(기존 호출 호환).
 */
@Schema(name = "DuffImportRequest", description = "매출일괄등록(더프) 실행 요청")
public record DuffImportRequest(

        @Schema(description = "신청일 시작(미지정 시 올해 1/1)") LocalDate fromDate,
        @Schema(description = "신청일 종료(미지정 시 오늘)") LocalDate toDate,
        @Schema(description = "조회모드(ALL/PROCESSED/UNPROCESSED/REGISTERED)") DuffChargeMode mode,
        @Schema(description = "발송완료건만", example = "false") Boolean onlyComplete,

        @Schema(description = """
                등록할 줄의 **소스키 목록**. 미리보기 응답의 `sourceKey` 를 그대로 보낸다.
                ★비우면 기간 전체가 대상이다(예전 동작).
                ‼️미리보기와 **같은 기간·모드**를 함께 보내야 한다 — 범위가 다르면
                그 소스키가 목록에 없어 조용히 빠진다.""")
        List<String> sourceKeys,

        @Schema(description = """
                체크한 줄 **전부에 적용할 상품**(도서) id. 레거시의 아래 콤보박스가 이것이다.
                ★비우면 과목코드로 상품을 찾는다(예전 동작) — 못 찾으면 그 줄은 '상품 없음'으로 빠진다.
                DSRE 과목(시행일×영역)과 우리 도서는 1:1이 아니라, 담당자가 고르는 것이 원칙이다.""")
        Long productId,

        @Schema(description = """
                거래일자. **비우면 신청일 기준**으로 줄마다 다르게 들어간다
                (레거시의 '신청일기준' 체크박스와 같다).""")
        LocalDate salesDate,

        @Schema(description = "등록되는 모든 줄에 붙일 공통 메모") String memo,

        @Schema(description = "줄별 수정값 — 보낸 줄만 바뀐다") List<Override> overrides
) {
    /**
     * 한 줄의 수정값. <b>보낸 칸만</b> 바뀐다(null = 원래 값 유지).
     * 레거시의 '금액수정가능'이 이것이다.
     */
    @Schema(name = "DuffImportOverride", description = "줄별 수정값")
    public record Override(
            @Schema(description = "대상 줄의 소스키", requiredMode = Schema.RequiredMode.REQUIRED)
            String sourceKey,
            @Schema(description = "청구인원") Integer inwon,
            @Schema(description = "정가") Integer unitPrice,
            @Schema(description = "공급률(%)") Integer supplyRate,
            @Schema(description = "할인액") Integer discount,
            @Schema(description = """
                    총금액을 직접 지정. ‼️주면 정가·공급률·인원으로 **계산하지 않고 이 값을 쓴다** —
                    담당자가 끝수를 맞춘 금액이 계산으로 덮이면 안 된다.""")
            Long totalAmount
    ) {
    }

    /** 소스키 필터가 걸려 있나. 비어 있으면 기간 전체가 대상이다. */
    public boolean hasSelection() {
        return sourceKeys != null && !sourceKeys.isEmpty();
    }

    public boolean onlyCompleteOrFalse() {
        return Boolean.TRUE.equals(onlyComplete);
    }
}
