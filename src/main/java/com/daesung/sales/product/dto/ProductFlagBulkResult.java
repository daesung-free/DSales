package com.daesung.sales.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 일괄 변경 결과.
 *
 * <p>{@code requested}와 {@code changed}를 나눠 돌려준다 — 100건을 골랐는데 이미 같은 값이라
 * 실제로 바뀐 건 3건일 수 있다. "100건 적용됨"만 보여주면 담당자가 무엇이 일어났는지 모른다.
 */
@Schema(name = "ProductFlagBulkResult", description = "도서 Y/N 일괄 변경 결과")
public record ProductFlagBulkResult(
        @Schema(description = "요청한 도서 수") int requested,
        @Schema(description = "실제로 값이 바뀐 도서 수") int changed,
        @Schema(description = "값이 이미 같아 건드리지 않은 도서 수") int unchanged,
        @Schema(description = "존재하지 않아 건너뛴 도서 id") List<Long> notFoundIds
) {
}
