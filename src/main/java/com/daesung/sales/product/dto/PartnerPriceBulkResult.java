package com.daesung.sales.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 거래처별 단가 일괄 적용 결과.
 *
 * <p>건너뛴 거래처를 <b>코드까지</b> 돌려준다 — "몇 건 적용됨"만 보여주면 담당자는 예외 단가가
 * 지켜진 건지 누락된 건지 구분할 수 없다.
 */
@Schema(name = "PartnerPriceBulkResult", description = "거래처별 단가 일괄 적용 결과")
public record PartnerPriceBulkResult(

        @Schema(description = "도서 id") Long productId,
        @Schema(description = "도서코드") String productCode,
        @Schema(description = "신규 생성된 매핑 수") int created,
        @Schema(description = "덮어쓴 매핑 수(overwrite=true일 때만)") int updated,
        @Schema(description = "기존 매핑이 있어 건너뛴 수(overwrite=false)") int skipped,
        @Schema(description = "건너뛴 거래처코드 목록") List<String> skippedPartnerCodes
) {
}
