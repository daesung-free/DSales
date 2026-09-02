package com.daesung.sales.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 마스터 엑셀 업로드 결과(33p 도서관리 3탭 공용).
 * 근거: 발주처 화면검토(2026-08-31) 화면33 — "도서관리 등록 탭(기본정보/세트구성/거래처별단가)
 * 전체에 엑셀 업로드 기능 신설".
 *
 * <p>★<b>행 단위로 결과를 준다.</b> 마스터 업로드는 수백 줄을 한 번에 올리는 일이라
 * "실패했습니다" 한 줄로 끝내면 담당자가 어느 줄을 고쳐야 하는지 알 수 없다.
 * 엑셀 행번호를 그대로 실어 그 줄을 찾아갈 수 있게 한다.
 *
 * <p>★<b>신규와 수정을 나눠 센다.</b> 고치려고 올렸는데 전부 '신규'로 잡히면
 * 코드가 안 맞아 딴 걸 만든 것이고, 반대면 덮어쓴 것이다 — 숫자만 봐도 사고를 알아챈다.
 */
@Schema(description = "마스터 엑셀 업로드 결과. created/updated가 예상과 다르면 키(코드)가 안 맞은 것이다")
public record MasterUploadResponse(

        @Schema(description = "신규 등록된 행 수") int created,
        @Schema(description = "기존 값을 고친 행 수") int updated,
        @Schema(description = "실패한 행 수. 0이 아니면 lines에서 사유를 볼 것") int failed,
        @Schema(description = "행별 결과") List<Line> lines
) {
    @Schema(name = "MasterUploadLine")
    public record Line(
            @Schema(description = "엑셀 행번호(1-base)") int row,
            @Schema(description = "CREATED / UPDATED / ERROR") String result,
            @Schema(description = "그 행의 식별 키(도서코드·거래처코드 등)") String key,
            @Schema(description = "실패 사유(성공이면 null)") String message
    ) {
    }
}
