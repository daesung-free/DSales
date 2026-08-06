package com.daesung.sales.sale.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 매출 엑셀 업로드 결과. 근거: 레거시 매출가져오기.vb(salesData 일괄 INSERT, 재고 미반영).
 * 행별 처리결과(등록/오류) + 요약. 재고는 건드리지 않음(매출 원장만).
 */
public record SalesUploadResponse(int imported, int failed, List<Line> lines) {

    @Schema(name = "SalesUploadLine")

    public record Line(
            int row,             // 엑셀 행번호(1-base)
            String result,       // IMPORTED / ERROR
            String salesNo,      // 등록 성공 시 매출번호
            String custCode,
            String productCode,
            int qty,
            long supplyAmount,
            String message       // 오류 사유
    ) {
    }
}
