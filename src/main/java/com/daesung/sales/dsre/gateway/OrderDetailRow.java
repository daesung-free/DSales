package com.daesung.sales.dsre.gateway;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 주문 상세 — 반과 과목 수량. 등록이 3단인데 조회는 마스터만 있어
 * 화면이 상세 칸을 비워 두고 있었다(2026-09-18 지적).
 */
@Schema(name = "OrderDetailRow", description = "주문 상세(반 → 과목수량)")
public record OrderDetailRow(
        @Schema(description = "반 순번(SEQ)") int seq,
        @Schema(description = "반 이름") String className,
        @Schema(description = "신청방식 S=간편 / N=과목") String applyType,
        @Schema(description = "간편신청 — 인문 인원") Integer humanities,
        @Schema(description = "간편신청 — 자연 인원") Integer science,
        @Schema(description = "간편신청 — 통합 인원") Integer combined,

        @Schema(description = """
                이 반의 수량. 간편신청이면 인문+자연+통합, 과목신청이면 과목 수량 합이다 —
                목록의 총수량과 같은 방식으로 센다(두 화면 숫자가 갈리지 않게).""")
        long totalQty,

        @Schema(description = "과목별 수량(간편신청이면 비어 있다)") List<SubjectQtyRow> subjects
) {
    @Schema(name = "OrderSubjectQtyRow")
    public record SubjectQtyRow(
            @Schema(description = "과목코드") int resCd,
            @Schema(description = "과목명") String resName,
            @Schema(description = "신청 수량") int qty
    ) {
    }
}
