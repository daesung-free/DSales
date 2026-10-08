package com.daesung.sales.closing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 계산서신고 수기 보정 저장(발주처 1차회신 2026-10-02 #24).
 *
 * <p>★<b>보낸 값으로 통째 교체한다.</b> 비운 칸은 "보정을 지웠다"는 뜻이다 —
 * null 을 무시하면 한번 넣은 보정을 되돌릴 수 없다.
 */
@Schema(name = "TaxInvoiceAdjustRequest", description = "계산서 수기 보정(보낸 값으로 교체)")
public record TaxInvoiceAdjustRequest(

        @Schema(description = """
                작성일자 — 보정할 계산서를 찾는 키다. 조회 화면의 `issueDate` 를 그대로 보낸다
                (월 단위 조회면 그 달 말일).""",
                example = "2026-08-31", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalDate issueDate,

        @Schema(description = "공급받는자 거래처 id", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long partnerId,

        @Schema(description = "과세구분 — FREE(면세 05) / TAXABLE(과세 01)",
                example = "FREE", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Pattern(regexp = "FREE|TAXABLE") String taxBucket,

        @Schema(description = "상호 보정(비우면 거래처 마스터 값)") @Size(max = 100) String partnerName,
        @Schema(description = "사업자번호 보정") @Size(max = 20) String bizNo,
        @Schema(description = "대표자 보정") @Size(max = 50) String bossName,
        @Schema(description = "사업장주소 보정") @Size(max = 255) String address,
        @Schema(description = "업태 보정") @Size(max = 50) String bizStatus,
        @Schema(description = "종목 보정") @Size(max = 50) String bizItem,
        @Schema(description = "이메일1 보정") @Size(max = 100) String email1,
        @Schema(description = "이메일2 보정") @Size(max = 100) String email2,

        @Schema(description = """
                공급가액 합계 보정(선택). 비우면 매출에서 파생한 값을 그대로 쓴다.
                ‼️값을 넣으면 응답에 `adjusted=true` 와 **원래 숫자**가 함께 내려간다 —
                장부와 다르다는 사실을 숨기지 않기 위해서다.
                ★품목·수량이 틀린 것이라면 여기가 아니라 **매출을 고쳐야 한다**
                (`PATCH /sales/{id}`). 여기서 덮으면 계산서만 맞고 장부는 틀린 채 남는다.""")
        Long supplyTotal,

        @Schema(description = "세액 합계 보정(선택). 비우면 파생값 그대로") Long taxTotal,

        @Schema(description = "영수(01)/청구(02). 미지정이면 02", example = "02")
        @Pattern(regexp = "01|02") String receiptType,

        @Schema(description = "비고") @Size(max = 500) String memo,

        @Schema(description = "보정 사유 — 감사에서 실제로 묻는 것은 \"왜\"다")
        @Size(max = 500) String reason
) {
}
