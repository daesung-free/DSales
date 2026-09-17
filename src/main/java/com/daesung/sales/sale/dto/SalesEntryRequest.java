package com.daesung.sales.sale.dto;

import com.daesung.sales.sale.entity.ProcType;
import com.daesung.sales.salestype.entity.ShipmentType;
import com.daesung.sales.sale.entity.PackType;
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

        @Schema(description = """
                출고 물류창고 id(정상출고=−차감 / 반품=+복구).

                <p>★<b>비워 둘 수 있다 — '미출고 매출'이다.</b> 문항사용료·학원매출처럼
                **실제 출고가 일어나지 않는 매출**은 창고가 없다(정본 13p 필요기능).
                이 경우 재고를 건드리지 않고 매출만 선다.
                ‼️거래명세서·물류 작업으로도 연계되지 않는다 — 내보낼 물건이 없기 때문이다.

                <p>‼️<b>반품·정상출고는 창고가 필요하다.</b> 재고가 움직이는 거래라
                어느 창고인지 없으면 어디서 빼고 어디로 넣을지 알 수 없다 → 미지정 시 400.""",
                example = "1")
        Long warehouseId,

        @Schema(description = "매출 품목 목록", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty @Valid List<Item> items
) {
    /** 매출 품목. 금액=정가×공급률/100×수량(부수기준), 세액=면세면 0 아니면 공급가액의 10%. */
    @Schema(name = "SalesEntryItem")
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

            @Schema(description = """
                    권당 할인액(선택). 미입력 시 거래처×대분류 매핑(34p)에서 자동조회.
                    **값이 있으면 공급률 대신 이 값으로 금액이 계산된다** — 공급가액 = (정가−할인액)×수량.
                    둘 다 곱하면 이중 할인이 되기 때문이다(레거시 매출가져오기.vb:425).""",
                    example = "0")
            @PositiveOrZero Integer discountAmount,

            @Schema(description = "수량", example = "100", requiredMode = Schema.RequiredMode.REQUIRED)
            @Positive int qty,

            @Schema(description = "세액(선택). 자동산출하지 않으며 미입력 시 0. 면세 상품에는 입력 불가",
                    example = "0")
            @PositiveOrZero Integer tax,

            @Schema(description = "성적처리 구분(37p 월별매출액명세서 인원 집계축): GRADED(성적처리)/UNGRADED(비처리). 미지정 시 비처리로 집계",
                    example = "GRADED")
            ProcType procType,

            @Schema(description = "학교/학원 코드(12p 세부 거래단위, 선택)", example = "A0003")
            String schoolCode,

            @Schema(description = "학교/학원명(선택)", example = "진주고등학교")
            String schoolName,

            @Schema(description = "세트 상품 회차(선택)", example = "4")
            Integer round,

            @Schema(description = "포장구분(물류 회차별 작업현황 집계축, 선택): "
                    + "INDIVIDUAL_1(개별1·개별봉투)/INDIVIDUAL_2(개별2·개별봉투SET)/CLASS_BUNDLE(반별)",
                    example = "INDIVIDUAL_1")
            PackType packType,

            @Schema(description = "비고", example = "6월 정상 매출")
            String memo
    ) {
    }
}
