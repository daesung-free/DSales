package com.daesung.sales.sale.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 매출일괄등록(DSRE) 결과. dryRun=true면 미리보기(쓰기 없음). */
public record BulkImportResponse(
        @Schema(description = "미리보기 여부(true=쓰기 안 함)") boolean dryRun,
        @Schema(description = "생성된 매출 라인 수") int importedCount,
        @Schema(description = "이미 등록되어 스킵된 라인 수(멱등)") int skippedCount,
        @Schema(description = "거래처/도서 매핑 실패로 제외된 라인 수") int unmappedCount,
        @Schema(description = "라인 상세") List<Line> lines
) {
    @Schema(name = "BulkImportLine")
    public record Line(
            @Schema(description = "DSRE 소스키(req:lst:dtl:구분)") String sourceKey,
            @Schema(description = "구분(SALE=매출/GIFT=증정/TEACHER_USE=무상)") String kind,
            @Schema(description = "거래처") String partner,
            @Schema(description = "도서") String book,
            @Schema(description = "수량") int qty,
            @Schema(description = "공급가액") long supplyAmount,
            @Schema(description = "처리 결과(IMPORTED/SKIPPED/UNMAPPED/PREVIEW)") String result,
            @Schema(description = "생성 매출번호(등록 시)") String salesNo
    ) {
    }
}
