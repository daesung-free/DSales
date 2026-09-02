package com.daesung.sales.product.entity;

/**
 * 자재구분. 자재 마스터(V57)와 BOM 상세(33p)가 함께 쓴다.
 *
 * <p>★<b>물류 작업비 단가를 고르는 유일한 키</b>다. 발주처 구조보완요청안(2026-08-31) 원문 —
 * "물류 작업비는 세트구성(BOM)의 자재별 소요수량에 그 시행의 해당 자재구분 단가
 * (시험지→시험지, OMR→OMR, 단행본·책자→단행본, 라벨→라벨, <b>해설지→시험지</b>)를 곱해
 * 계산하는 기존 로직을 그대로 사용하므로, 자재 마스터에 별도 비용 연계 필드는 필요하지
 * 않습니다. <b>등록 시 자재구분을 정확히 선택하는 것이 중요합니다.</b>"
 *
 * <p>‼️<b>해설지는 시험지 단가를 쓴다.</b> 자재 목록에서는 시험지와 별개로 유지하지만
 * (별도 소요수량 관리·재고관리 목적) 단가는 시험지를 따른다 — 해설지 단가는 신설하지 않는다.
 * {@link #rateKey()}가 그 매핑을 한 곳에 담는다.
 */
public enum MaterialType {
    /** 시험지 */
    EXAM_PAPER("시험지"),
    /** 해설지 */
    ANSWER_SHEET("해설지"),
    /** OMR 카드 */
    OMR("OMR"),
    /** 라벨 */
    LABEL("라벨"),
    /** 단행본·책자 */
    BOOK("단행본"),
    /** 기타 자재 */
    ETC("기타");

    private final String label;

    MaterialType(String label) {
        this.label = label;
    }

    /** 화면·엑셀 표기. 담당자는 EXAM_PAPER가 아니라 '시험지'라고 적는다. */
    public String label() {
        return label;
    }

    /**
     * 물류비용등록(36p)에서 어느 단가 컬럼을 볼지. 발주처 확정 매핑이다.
     *
     * <p>해설지가 시험지로 접히는 것이 핵심 — 이 매핑을 여러 곳에 흩어 두면
     * 한 곳만 고쳤을 때 작업비가 조용히 갈린다.
     */
    public MaterialType rateKey() {
        return (this == ANSWER_SHEET) ? EXAM_PAPER : this;
    }
}
