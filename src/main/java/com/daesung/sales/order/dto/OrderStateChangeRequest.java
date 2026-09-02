package com.daesung.sales.order.dto;

import com.daesung.sales.dsre.gateway.OrderState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 진행상태 다건 일괄 전환 요청(화면3).
 * 근거: 자료요청서 3-2(가).진행상태 — 되돌리기(수동) · 발송완료(체크박스 다건 일괄).
 */
public record OrderStateChangeRequest(

        @Schema(description = "대상 신청번호(REQ_CD) 목록. 좌측 체크박스로 고른 것들",
                example = "[78331, 78332]", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty List<Integer> reqCds,

        @Schema(description = """
                목표 상태. 매출프로그램에서 만들 수 있는 값은 셋뿐이다 —
                `PREPARING`(상품준비중, 되돌리기) · `READY_TO_SHIP`(발송준비중) · `SHIPPED`(발송완료).
                접수·검수·취소는 order 사이트와 DSRE2 소관이라 거부된다.""",
                example = "SHIPPED", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull OrderState toState,

        @Schema(description = "변경 사유. 되돌리기는 적어 두는 편이 좋다 — 감사에서 묻는 것은 \"왜\"다. "
                + "비워 두면 이력에 '사유 미입력'으로 남는다",
                example = "물류 재검수 필요로 되돌림")
        String reason
) {
}
