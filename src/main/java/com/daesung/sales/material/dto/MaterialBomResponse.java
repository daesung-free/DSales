package com.daesung.sales.material.dto;

import com.daesung.sales.material.entity.MaterialBom;
import com.daesung.sales.product.entity.MaterialType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 세트의 자재 매칭 목록(33p '구성회차별 매칭 결과'). */
public record MaterialBomResponse(

        @Schema(description = "세트 상품 id") Long setProductId,
        @Schema(description = "세트 도서코드") String setCode,
        @Schema(description = "세트명") String setName,
        @Schema(description = "매칭 목록(공통 먼저, 그다음 회차순)") List<Row> rows
) {
    @Schema(name = "MaterialBomRow")
    public record Row(
            @Schema(description = "매칭 id") Long id,
            @Schema(description = "회차 상품 id(공통이면 null)") Long roundProductId,
            @Schema(description = "구성회차 표기 — 공통이면 '공통'", example = "1회") String roundLabel,
            @Schema(description = "자재 id") Long materialId,
            @Schema(description = "자재코드") String materialCode,
            @Schema(description = "자재명") String materialName,
            @Schema(description = "자재구분") MaterialType materialType,
            @Schema(description = "세트당 소요수량") int qtyPerSet,
            @Schema(description = "회차 반복형 여부(공통 자재 전용). true면 회차 단독 출고에도 자재가 나간다")
            boolean perRound
    ) {
    }

    public static MaterialBomResponse of(Long setId, String setCode, String setName,
                                         List<MaterialBom> boms) {
        List<Row> rows = boms.stream()
                .map(b -> new Row(
                        b.getId(),
                        b.isCommon() ? null : b.getRoundProduct().getId(),
                        b.isCommon() ? "공통" : b.getRoundProduct().getName(),
                        b.getMaterial().getId(), b.getMaterial().getCode(),
                        b.getMaterial().getName(), b.getMaterial().getMaterialType(),
                        b.getQtyPerSet(), b.isPerRound()))
                .toList();
        return new MaterialBomResponse(setId, setCode, setName, rows);
    }
}
