package com.daesung.sales.material.dto;

import com.daesung.sales.material.entity.Material;
import com.daesung.sales.product.entity.MaterialType;
import io.swagger.v3.oas.annotations.media.Schema;

/** 자재 마스터 한 건. */
public record MaterialResponse(

        @Schema(description = "자재 id") Long id,
        @Schema(description = "자재코드") String code,
        @Schema(description = "자재명") String name,
        @Schema(description = "자재구분") MaterialType materialType,
        @Schema(description = "자재구분 표기") String materialTypeName,
        @Schema(description = """
                물류 작업비 단가 키. 해설지(ANSWER_SHEET)는 **시험지(EXAM_PAPER)** 로 접힌다 —
                해설지 단가는 신설하지 않는다는 발주처 확정 때문이다.""")
        MaterialType rateKey,
        @Schema(description = "사용여부") boolean useYn,
        @Schema(description = "비고") String memo
) {
    private static String label(MaterialType t) {
        return switch (t) {
            case EXAM_PAPER -> "시험지";
            case ANSWER_SHEET -> "해설지";
            case OMR -> "OMR";
            case LABEL -> "라벨";
            case BOOK -> "단행본";
            case ETC -> "기타";
        };
    }

    public static MaterialResponse from(Material m) {
        return new MaterialResponse(m.getId(), m.getCode(), m.getName(), m.getMaterialType(),
                label(m.getMaterialType()), m.getMaterialType().rateKey(), m.isUseYn(), m.getMemo());
    }
}
