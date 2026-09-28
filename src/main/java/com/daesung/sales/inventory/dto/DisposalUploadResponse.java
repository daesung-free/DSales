package com.daesung.sales.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 폐기 일괄 등록(엑셀) 결과.
 *
 * <p>★<b>한 건이라도 오류면 아무것도 등록되지 않는다.</b> 그때 {@code disposalNo} 는 비어 있고
 * {@code lines} 에 어느 줄이 왜 틀렸는지가 담긴다. 폐기는 재고를 깎는 전표라
 * 절반만 들어가면 담당자가 파일과 대조해야 해서, 전량 검증 후에만 등록한다.
 */
public record DisposalUploadResponse(

        @Schema(description = "등록된 폐기 전표번호. **오류가 있거나 dryRun 이면 null**", example = "P-20260608-1")
        String disposalNo,
        @Schema(description = "정상 행 수") int ok,
        @Schema(description = "오류 행 수. 0이 아니면 등록은 일어나지 않았다") int failed,
        @Schema(description = "창고명(등록된 경우)") String warehouseName,
        @Schema(description = "행별 결과") List<Line> lines
) {
    @Schema(name = "DisposalUploadLine")
    public record Line(
            @Schema(description = "엑셀 행번호(1-base) — 담당자가 그 줄을 찾을 수 있어야 한다") int row,
            @Schema(description = "OK / ERROR") String result,
            @Schema(description = "상품코드") String code,
            @Schema(description = "도서명(찾은 경우)") String productName,
            @Schema(description = "폐기수량") int qty,
            @Schema(description = "오류 사유") String message
    ) {
    }
}
