package com.daesung.sales.product.dto;

import com.daesung.sales.product.entity.BomItem;
import com.daesung.sales.product.entity.MaterialType;
import com.daesung.sales.product.entity.Product;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/** BOM 구성 응답(완제품 + 구성품 목록). 33p 세트구성 상세 탭 컬럼. */
public record BomResponse(
        Long parentId,
        String parentCode,
        String parentName,
        List<Component> components
) {
    /** 33p 세트구성 상세 탭 1행. */
    @Schema(name = "BomComponent")
    public record Component(
            @Schema(description = "자재(구성품) 상품 id") Long childProductId,
            @Schema(description = "자재코드") String childCode,
            @Schema(description = "자재명") String childName,
            @Schema(description = "세트당 소요수량") int ratio,
            @Schema(description = "구성회차(0=구분 없음)") int round,
            @Schema(description = "시행예정일") LocalDate examDate,
            @Schema(description = "분리포장여부") boolean separatePack,
            @Schema(description = "자재구분") MaterialType materialType,
            @Schema(description = "물류비용 연계(작업구분 PACKTYPE)") Integer packType) {
    }

    public static BomResponse from(Product parent, List<BomItem> items) {
        List<Component> comps = items.stream()
                .map(b -> new Component(b.getChild().getId(), b.getChild().getCode(),
                        b.getChild().getName(), b.getRatio(), b.getRound(), b.getExamDate(),
                        b.isSeparatePack(), b.getMaterialType(), b.getPackType()))
                .toList();
        return new BomResponse(parent.getId(), parent.getCode(), parent.getName(), comps);
    }
}
