package com.daesung.sales.logistics.dto;

import com.daesung.sales.logistics.entity.WorkType;
import io.swagger.v3.oas.annotations.media.Schema;

/** 작업구분 응답(36p 작업구분 관리 탭). */
public record WorkTypeResponse(
        @Schema(description = "id") Long id,
        @Schema(description = "DSRE2 PACKTYPE 값(불변)") int packType,
        @Schema(description = "작업구분명") String name,
        @Schema(description = "시험지") int paper,
        @Schema(description = "OMR") int omr,
        @Schema(description = "단행본") int etc,
        @Schema(description = "라벨") int label,
        @Schema(description = "기본작업비") int basic,
        @Schema(description = "출고비") int trade,
        @Schema(description = "사용여부") boolean useYn,
        @Schema(description = "정렬 순서") int sortOrder
) {
    public static WorkTypeResponse from(WorkType w) {
        return new WorkTypeResponse(w.getId(), w.getPackType(), w.getName(),
                w.getPaper(), w.getOmr(), w.getEtc(), w.getLabel(), w.getBasic(), w.getTrade(),
                w.isUseYn(), w.getSortOrder());
    }
}
