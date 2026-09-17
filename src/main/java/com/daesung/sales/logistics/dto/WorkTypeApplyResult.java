package com.daesung.sales.logistics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 작업구분 기준단가 일괄 반영 결과(36p).
 *
 * <p>건너뛴 행을 <b>시행코드까지</b> 돌려준다 — "몇 건 적용"만 보여주면 담당자는
 * 예외가 지켜진 것인지 누락인지 구분할 수 없다(거래처별 단가 일괄적용과 같은 규칙).
 */
@Schema(name = "WorkTypeApplyResult", description = "작업구분 단가 일괄 반영 결과(미리보기 공용)")
public record WorkTypeApplyResult(
        @Schema(description = "작업구분(PACKTYPE)") int packType,
        @Schema(description = "작업구분명") String name,

        @Schema(description = """
                미리보기 여부. **true면 아무것도 바꾸지 않았다** — `applied`는 "덮일 행 수"라는 뜻이다.
                화면이 둘을 같은 문구로 보여주면 담당자가 이미 반영된 줄 안다.""")
        boolean preview,

        @Schema(description = "단가가 반영된(미리보기면 반영될) 행 수") int applied,
        @Schema(description = "대상 시행코드 목록 — 미리보기에서 '무엇이 덮이는지' 보여주려면 건수만으론 부족하다")
        List<Integer> appliedDtlCds,
        @Schema(description = "예외로 등록돼 건너뛴 행 수") int skipped,
        @Schema(description = "건너뛴 시행코드 목록") List<Integer> skippedDtlCds,

        @Schema(description = """
                그중 **매출프로그램 상품 단가**(우리 DB) 건수. 나머지는 DSRE 시행 단가다.
                두 축이 한 버튼으로 반영되므로 어느 쪽 몇 건인지 갈라 준다.""")
        int ourProductCount,

        @Schema(description = """
                DSRE 시행 단가까지 반영됐는지. **false면 DSRE 연동이 꺼져 있어 우리 상품 단가만 반영됐다** —
                화면이 "전부 반영됨"으로 보여주면 담당자가 DSRE 쪽도 바뀐 줄 안다.""")
        boolean dsreApplied
) {
}
