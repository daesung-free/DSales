package com.daesung.sales.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 대시보드 <b>할 일</b> 카드 한 장. 필드 구성은 화면의 {@code ActionItem} 타입 그대로다 —
 * 화면이 이미 이 형태로 목업을 돌리고 있어 서버가 맞춰 주면 스위치만 켜면 된다.
 *
 * <p>★<b>0건인 항목은 내려보내지 않는다.</b> "정산 대기 0건" 카드가 떠 있으면
 * 할 일이 없다는 사실을 매번 읽어서 확인해야 한다 — 목록에 있다는 것 자체가 신호여야 한다.
 */
@Schema(name = "ActionItem", description = "대시보드 할 일 카드")
public record ActionItemResponse(

        @Schema(description = "항목 키(settle/workreq/collateral)", example = "settle") String key,
        @Schema(description = "표시 라벨", example = "위탁 정산 대기") String label,
        @Schema(description = "건수", example = "3") int count,
        @Schema(description = "클릭 시 이동할 화면 경로", example = "/sales/entry") String to,
        @Schema(description = "심각도(info/warning/danger)", example = "warning") String tone,
        @Schema(description = "부가 설명", example = "미결잔여 1,200부") String hint
) {
    /** 심각도. 이미 벌어진 일(만료)은 danger, 다가오는 일은 warning, 단순 안내는 info. */
    public static final String INFO = "info";
    public static final String WARNING = "warning";
    public static final String DANGER = "danger";
}
