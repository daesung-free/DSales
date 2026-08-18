package com.daesung.sales.logistics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 물류단가 일괄 수정 결과. */
@Schema(name = "LogisCostBulkResult", description = "물류단가 일괄 수정 결과")
public record LogisCostBulkResult(
        @Schema(description = "요청 건수") int requested,
        @Schema(description = "실제로 값이 바뀐 건수") int changed,
        @Schema(description = "값이 이미 같아 건드리지 않은 건수") int unchanged,
        @Schema(description = "단가가 없어 건너뛴 시행코드") List<Integer> notFoundDtlCds
) {
}
