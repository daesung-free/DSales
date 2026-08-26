package com.daesung.sales.dsre.gateway;

/**
 * 매출일괄등록(14p) <b>처리구분</b> 조회 모드. 근거: 레거시 매출가져오기 화면 콤보
 * {@code ComboBox_처리구분}("모두"/"처리"/"비처리")와 안내문
 * {@code 매출가져오기.Designer.vb:534} — "처리구분에 따른 단가 표기 및 매출 등록".
 *
 * <p>이 값은 두 곳에 동시에 작용한다. 그래서 조회 필터가 아니라 <b>모드</b>다 —
 * <ol>
 *   <li><b>정가</b>: {@code tbl_product_amt}는 처리/비처리(proc_gn)별로 단가가 다르다.
 *       어느 쪽 단가를 볼지가 여기서 정해진다.</li>
 *   <li><b>보이는 행</b>: 레거시가 {@code ifCondition}으로 거르는 조건과 같다.</li>
 * </ol>
 */
public enum DuffChargeMode {

    /** 모두 — 신청건의 처리구분(proc_yn2)을 그대로 따른다. 레거시 기본값. */
    ALL("A"),

    /** 처리 — 처리 단가로 보고, 처리구분이 'N'인 건은 뺀다. */
    PROCESSED("Y"),

    /**
     * 비처리 — 비처리 단가로 본다.
     * 남기는 행은 {@code 처리구분='N'} 이거나, 처리건이라도 <b>청구구분 T</b>(처리+비처리)인 것.
     * T는 처리분과 비처리분을 함께 청구하는 계약이라 비처리로 볼 때도 대상이다.
     */
    UNPROCESSED("N");

    private final String rateCode;

    DuffChargeMode(String rateCode) {
        this.rateCode = rateCode;
    }

    /** 정가 조인({@code tbl_product_amt.proc_gn})에 넘길 코드. A면 신청건의 처리구분을 따른다. */
    public String rateCode() {
        return rateCode;
    }

    /** 이 모드에서 해당 행을 보여줄지. 레거시 {@code ifCondition}과 같은 판정. */
    public boolean accepts(String procType, String chargeType) {
        return switch (this) {
            case ALL -> true;
            case PROCESSED -> !"N".equals(procType);
            case UNPROCESSED -> "N".equals(procType) || "T".equals(chargeType);
        };
    }
}
