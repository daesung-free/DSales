package com.daesung.sales.inventory.dto;

import com.daesung.sales.inventory.entity.InboundType;
import com.daesung.sales.inventory.entity.TxnType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * 입고/대체 내역 한 줄(8p·9p 조회). 필드 구성은 화면의 {@code InboundRecord} 타입 그대로다.
 *
 * <p>★<b>등록만 되고 조회가 없었다.</b> 폐기와 같은 상황이다 —
 * 입고·이고·세트작업은 전부 재고를 움직이는데, 되짚어 볼 방법이 없으면
 * 재고가 안 맞을 때 "언제 무엇이 들어오고 옮겨졌는지"를 알 수 없다.
 *
 * <p>수불부({@code /stock/ledger})는 <b>합계</b>를 주고 이건 <b>낱건</b>을 준다.
 * 합계가 이상할 때 그 안을 들여다보는 용도라 둘 다 필요하다.
 */
public record StockRecordRow(

        @Schema(description = "재고이벤트 id(화면의 recordId)") Long recordId,
        @Schema(description = "처리일자") LocalDate date,
        @Schema(description = "작업구분(입고/단순이고/세트조립/세트해체)", example = "입고") String kind,
        @Schema(description = "창고 id") Long warehouseId,
        @Schema(description = "창고명") String warehouse,
        @Schema(description = "상품 id") Long productId,
        @Schema(description = "도서코드") String bookCode,
        @Schema(description = "도서명") String bookName,
        @Schema(description = "분류코드") String catCode,
        @Schema(description = "분류명") String catName,
        @Schema(description = """
                재고 증감(+입고·조립생성 / −이고출발·조립소모).
                ★부호를 그대로 준다 — 폐기와 달리 여기서는 방향이 정보다.
                이고는 출발(−)·도착(+) 두 줄로 나오고, 세트작업도 완제품(+)·구성품(−)으로 나뉜다.""")
        int qtyDelta,
        @Schema(description = "입고구분(NORMAL/PURCHASE). 입고 행에만 있다 — 이고·세트작업은 매입이 아니다")
        InboundType inboundType,
        @Schema(description = "입고단가(원가). 매입입고에만 값이 있다 — 16p 순매출의 매입액이 여기서 나온다")
        Long unitCost,
        @Schema(description = "전표번호(입고/이고/세트작업 묶음 키)") String refNo,
        @Schema(description = "비고") String memo
) {

    /** 재고이벤트 유형 → 화면 표기. 화면이 매번 매핑표를 들고 있지 않도록 서버가 한글로 준다. */
    public static String kindOf(TxnType t) {
        return switch (t) {
            case INBOUND -> "입고";
            case TRANSFER -> "단순이고";
            case BOM_ASSEMBLE -> "세트조립";
            case BOM_DISASSEMBLE -> "세트해체";
            default -> t.name();
        };
    }
}
