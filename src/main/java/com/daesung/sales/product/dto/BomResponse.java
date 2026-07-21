package com.daesung.sales.product.dto;

import com.daesung.sales.product.entity.BomItem;
import com.daesung.sales.product.entity.Product;
import java.util.List;

/** BOM 구성 응답(완제품 + 구성품 목록). */
public record BomResponse(
        Long parentId,
        String parentCode,
        String parentName,
        List<Component> components
) {
    public record Component(Long childProductId, String childCode, String childName, int ratio) {
    }

    public static BomResponse from(Product parent, List<BomItem> items) {
        List<Component> comps = items.stream()
                .map(b -> new Component(b.getChild().getId(), b.getChild().getCode(),
                        b.getChild().getName(), b.getRatio()))
                .toList();
        return new BomResponse(parent.getId(), parent.getCode(), parent.getName(), comps);
    }
}
