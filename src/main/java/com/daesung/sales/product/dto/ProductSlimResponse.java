package com.daesung.sales.product.dto;

import com.daesung.sales.product.entity.ContentType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 도서 목록 <b>가벼운 응답</b>(프론트 요청 2026-10-20 B-2).
 *
 * <p>이관 뒤 도서가 14,808권이라 전체 목록이 약 5.4MB다. 화면이 실제로 쓰는 칸만 담으면
 * 약 3.9MB로 줄어든다. 여러 화면이 이 목록을 공유해 한 번 받아 나눠 쓰므로 체감이 크다.
 *
 * <p>★<b>기본 응답은 그대로 둔다.</b> 쓰는 칸을 말없이 빼면 그 칸을 보던 화면이 조용히 빈다.
 * {@code view=slim} 을 준 요청에만 이 모양으로 내보낸다.
 *
 * <p>담는 칸은 프론트가 적어 준 목록 그대로이고, 여기에 {@code updatedAt} 을 더했다 —
 * 바뀐 것만 받는 {@code updatedSince} 와 짝이라 가벼운 응답에서도 있어야 한다.
 */
@Schema(name = "ProductSlimResponse", description = "도서 목록(가벼운 응답)")
public record ProductSlimResponse(
        Long id,
        String code,
        String name,
        String catCode,
        String catName,
        String majorCategoryName,
        boolean set,
        String salesDivision,
        boolean useYn,
        Integer price,
        boolean taxFree,
        Integer supplyRate,
        ContentType contentType,
        String grade,
        boolean priceVisible,
        boolean stockManaged,
        LocalDateTime updatedAt
) {
    public static ProductSlimResponse from(ProductResponse p) {
        return new ProductSlimResponse(
                p.id(), p.code(), p.name(), p.catCode(), p.catName(), p.majorCategoryName(),
                p.set(), p.salesDivision(), p.useYn(), p.price(), p.taxFree(), p.supplyRate(),
                p.contentType(), p.grade(), p.priceVisible(), p.stockManaged(), p.updatedAt());
    }
}
