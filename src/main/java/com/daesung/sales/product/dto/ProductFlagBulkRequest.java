package com.daesung.sales.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 도서 Y/N 항목 <b>일괄 변경</b> 요청. 근거: 발주처 요청(자료요청서 1-3 회신) —
 * "Y/N 값들은 추후 변경될 수 있습니다. 각 열(Web게시여부·면세여부·수불부노출 등)을
 * 일괄로 처리할 수 있는 기능(전체선택 등)이 필요합니다."
 *
 * <p><b>지정하지 않은 플래그는 건드리지 않는다</b>(null = 그대로). 한 요청에서 여러 플래그를
 * 동시에 바꿀 수도 있고, 하나만 바꿀 수도 있다.
 */
@Schema(name = "ProductFlagBulkRequest", description = "도서 Y/N 항목 일괄 변경")
public record ProductFlagBulkRequest(

        @Schema(description = "대상 도서 id 목록(화면에서 선택한 행)",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty
        @Size(max = 1000, message = "한 번에 1000건까지 처리할 수 있습니다.")
        List<Long> productIds,

        @Schema(description = "Web게시여부 — 미지정이면 건드리지 않음", example = "true")
        Boolean webVisible,

        @Schema(description = "면세여부 — 미지정이면 건드리지 않음", example = "true")
        Boolean taxFree,

        @Schema(description = "수불부노출 — 미지정이면 건드리지 않음", example = "true")
        Boolean ledgerVisible,

        @Schema(description = "사용여부 — 미지정이면 건드리지 않음", example = "true")
        Boolean useYn,

        @Schema(description = "재고관리 여부 — 미지정이면 건드리지 않음. "
                + "끄면 매출 시 재고가 차감되지 않으니 주의", example = "true")
        Boolean stockManaged
) {
    /** 바꿀 플래그가 하나도 없으면 무의미한 요청이다(전 건을 훑고 아무것도 안 한다). */
    public boolean hasNoFlag() {
        return webVisible == null && taxFree == null && ledgerVisible == null
                && useYn == null && stockManaged == null;
    }
}
