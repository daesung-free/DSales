package com.daesung.sales.sale.dto;

import com.daesung.sales.sale.entity.ProcType;
import com.daesung.sales.salestype.entity.ShipmentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import java.util.List;

/** 수기 매출 등록 요청(일반 매출). 위탁출고 매출은 별도(위탁정산)로 처리. */
public record SalesEntryRequest(

        @Schema(description = "매출일자", example = "2026-06-22", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalDate salesDate,

        @Schema(description = "거래처 id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long partnerId,

        @Schema(description = "출고 물류창고 id(정상출고=−차감 / 반품=+복구)", example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long warehouseId,

        @Schema(description = "매출 품목 목록", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty @Valid List<Item> items
) {
    /** 매출 품목. 금액=정가×공급률/100×수량(부수기준), 세액=면세면 0 아니면 공급가액의 10%. */
    public record Item(

            @Schema(description = "상품 id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long productId,

            @Schema(description = "출고유형(CONSIGN_SHIP=위탁출고는 이 API 불가)", example = "NORMAL_SHIP",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull ShipmentType shipmentType,

            @Schema(description = "정가(원). 미입력 시 도서 마스터 정가 자동적용", example = "20000")
            @Positive Integer unitPrice,

            @Schema(description = "공급률(%). 미입력 시 거래처별 단가 매핑에서 자동조회(둘 다 없으면 오류)", example = "75")
            @PositiveOrZero Integer supplyRate,

            @Schema(description = "수량", example = "100", requiredMode = Schema.RequiredMode.REQUIRED)
            @Positive int qty,

            @Schema(description = "성적처리 구분(37p 월별매출액명세서 인원 집계축): GRADED(성적처리)/UNGRADED(비처리). 미지정 시 비처리로 집계",
                    example = "GRADED")
            ProcType procType,

            @Schema(description = "학교/학원 코드(12p 세부 거래단위, 선택)", example = "A0003")
            String schoolCode,

            @Schema(description = "학교/학원명(선택)", example = "진주고등학교")
            String schoolName,

            @Schema(description = "세트 상품 회차(선택)", example = "4")
            Integer round,

            @Schema(description = "비고", example = "6월 정상 매출")
            String memo
    ) {
    }
}
