package com.daesung.sales.inventory.dto;

import com.daesung.sales.inventory.entity.MaterialIo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;

/**
 * 자재 입출고 등록(입고 외 5종) — 출고 · 회수(사고처리) · 회수(반품) · 폐기 · 파손.
 *
 * <p>근거: 9/27 회의 항목 22 "dsre 자재입출고관리 참고". DSRE 화면이 자재·구분·수량을
 * 한 폼에서 받는 구조라 그대로 맞췄다 — 구분마다 엔드포인트를 쪼개면 화면이 다섯 벌이 된다.
 *
 * <p>입고만 따로 {@code POST /stock/materials/inbound} 다. 입고에는 <b>거래처와 입고단가</b>가
 * 붙는데 나머지 5종에는 없어서, 한 폼에 합치면 절반이 늘 비어 있는 요청이 된다.
 *
 * <p>‼️<b>사유 칸을 두지 않는다.</b> 9/27 회의 A-3(항목 9) — 폐기 사유 입력 제거 확정.
 * 비고는 남긴다 — 사유를 강제하지 않는 것과 메모를 못 쓰게 하는 것은 다르다.
 */
public record MaterialIoRequest(

        @Schema(description = "처리일자", example = "2026-06-08", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalDate processedDate,

        @Schema(description = "창고 id", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long warehouseId,

        @Schema(description = """
                입출고 구분 — 한글·코드명 모두 받는다.
                · `OUTBOUND`(출고) — 시행·작업으로 나감. **자재가 줄어드는 주 경로**
                · `RECOVER_ACCIDENT`(회수(사고처리용)) · `RECOVER_RETURN`(회수(반품)) — 되돌아옴
                · `DISPOSE`(폐기) · `DAMAGE`(파손) — 못 쓰게 됨

                ‼️`INBOUND`(입고)는 여기서 받지 않는다 — 거래처·단가가 필요해 전용 API 가 따로 있다.""",
                example = "OUTBOUND", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull MaterialIo io,

        @Schema(description = "자재 목록", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty @Valid List<Item> items
) {
    @Schema(name = "MaterialIoItem")
    public record Item(
            @Schema(description = "자재 id", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long materialId,
            @Schema(description = "수량 — **언제나 양수로 넣는다**. 나가는 구분이면 서버가 음수로 기록한다",
                    example = "120", requiredMode = Schema.RequiredMode.REQUIRED)
            @Positive int qty,
            @Schema(description = "비고", example = "인쇄 불량") String memo
    ) {
    }
}
