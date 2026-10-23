package com.daesung.sales.sale.dto;

import com.daesung.sales.sale.entity.PackType;
import com.daesung.sales.sale.entity.ProcType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 매출 수정(오입력 정정) 요청. 근거: 프론트 회신 2026-10-08 [A]2 —
 * 「통합매출 우클릭 [수정]」(9/27 회의 항목 12).
 *
 * <p>★<b>보내지 않은 항목은 건드리지 않는다.</b> 수량만 고치려다 메모나 회차가
 * 지워지면 담당자는 값이 사라진 것을 모른다(발송정보 입력과 같은 규칙).
 *
 * <p>★<b>바꿀 수 없는 것</b> — 거래일자 · 거래처 · 도서 · 출고유형 · 창고 · 학교.
 * 그 여섯이 바뀌면 사실상 <b>다른 거래</b>이고, 발송 건(작업요청서)을 묶는 키
 * (거래일·거래처·학교·분류)가 달라져 이미 나간 작업지시와 어긋난다.
 * 그 경우는 삭제 후 다시 등록해야 한다 — 이 API 는 거부하고 그렇게 안내한다.
 */
@Schema(name = "SaleUpdateRequest", description = "매출 수정(미지정 항목은 유지)")
public record SaleUpdateRequest(

        @Schema(description = """
                수량. 바꾸면 <b>재고도 차액만큼 보정</b>된다(원 이벤트를 고치지 않고
                같은 매출번호로 차액 이벤트를 남긴다 — 그래야 "그날 실제로 몇 개 나갔나"가 남는다).""",
                example = "15")
        @Positive Integer qty,

        @Schema(description = "정가", example = "12000") @Positive Integer unitPrice,

        @Schema(description = "공급률(%)", example = "70") @Positive Integer supplyRate,

        @Schema(description = """
                세액. ★<b>자동산출하지 않는다</b>(재무팀 확정) — 미입력이면 그대로 유지하고,
                보내면 그 값을 쓴다. 면세 상품에 0이 아닌 값을 보내면 400이다
                (조용히 무시하면 담당자가 값이 사라진 것을 모른다).""",
                example = "0")
        @PositiveOrZero Integer tax,

        @Schema(description = "할인액", example = "0") @PositiveOrZero Integer discountAmount,

        @Schema(description = """
                공급가액 **직접 입력**(선택, 현장 피드백 2026-10-14 B-3).
                값을 주면 그 금액으로 저장하고 `amountOverridden=true` 가 된다.

                비우면 **금액 산출 입력(수량·정가·공급률·할인액·세액)을 하나라도 보냈을 때만**
                다시 계산된다. 그 칸들을 하나도 안 보낸 수정(메모만 고치는 등)은
                **저장된 금액을 그대로 둔다.**

                ‼️예전엔 비우면 무조건 다시 계산해서, **메모만 고쳐도 금액이 0원**이 됐다 —
                  이관 자료에 수량·정가·공급률이 0이고 금액만 있는 줄이 있기 때문이다
                  (올해 116줄·약 42.9억. 프론트 실측 2026-10-23 §5). 0은 null 검사를 통과한다.""",
                example = "95000")
        @PositiveOrZero Long supplyAmount,

        @Schema(description = "메모") String memo,

        @Schema(description = "회차", example = "3") @Positive Integer bookRound,

        @Schema(description = "성적처리 구분(GRADED/미지정=비처리)") ProcType procType,

        @Schema(description = "포장구분") PackType packType
) {
    /** 바꿀 항목이 하나라도 있는지. 하나도 없으면 호출 자체가 실수다. */
    public boolean isEmpty() {
        return qty == null && unitPrice == null && supplyRate == null && tax == null
                && discountAmount == null && supplyAmount == null && memo == null
                && bookRound == null && procType == null && packType == null;
    }
}
