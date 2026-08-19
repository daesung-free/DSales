package com.daesung.sales.warehouse.dto;

import com.daesung.sales.warehouse.entity.Warehouse;
import com.daesung.sales.warehouse.entity.WarehouseType;
import io.swagger.v3.oas.annotations.media.Schema;

/** 창고 응답 DTO. 근거: 31p 창고관리 데이터 항목. */
public record WarehouseResponse(
        Long id,
        @Schema(description = "창고코드") String code,
        @Schema(description = "창고명") String name,
        @Schema(description = "창고구분 MAIN(물류창고)/CONSIGN(위탁창고)") WarehouseType type,
        @Schema(description = "실물재고여부. false(위탁창고)는 제품수불부 실재고에서 제외") boolean physicalStock,
        @Schema(description = "소속 거래처 id(위탁창고)") Long ownerClientId,
        @Schema(description = "소속 거래처명") String ownerClientName,
        @Schema(description = "사용여부. false면 목록에서 숨김 — 삭제하면 과거 재고 이벤트가 가리키는 창고가 사라진다")
        boolean useYn,
        @Schema(description = "비고") String memo
) {
    public static WarehouseResponse from(Warehouse w) {
        var owner = w.getOwnerClient();
        return new WarehouseResponse(w.getId(), w.getCode(), w.getName(), w.getType(),
                w.isPhysicalStock(),
                owner != null ? owner.getId() : null,
                owner != null ? owner.getName() : null,
                w.isUseYn(), w.getMemo());
    }
}
