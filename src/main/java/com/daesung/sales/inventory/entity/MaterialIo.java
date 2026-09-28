package com.daesung.sales.inventory.entity;

/**
 * 자재 입출고 구분. DSRE2 자재입출고관리({@code FM_LOGI_MatInOut.cs})의 콤보 6종을 그대로 옮긴 것이다.
 *
 * <p>근거: 9/27 회의 항목 22 — "도서관리-자재관리, 입고/대체등록에 자재 등록 내역 표시.
 * <b>dsre 자재입출고관리 참고</b>". 그 화면의 실제 코드가 이렇다:
 * <pre>
 *   입고 I · 회수(사고처리용) R · 회수(반품) B   → CNT + 수량
 *   출고 O · 폐기 T · 파손 P                    → CNT − 수량
 * </pre>
 *
 * <p>★<b>왜 {@link TxnType} 만으로는 안 되는가</b> —
 * 회수 2종이 둘 다 RETURN 이고 폐기·파손이 둘 다 DISPOSE 라, 재고 이벤트 종류로만 남기면
 * <b>서로 구분이 사라진다</b>. 담당자는 "폐기 300장"과 "파손 300장"을 다르게 보고한다.
 * 잔량 계산은 {@code TxnType} 이 하고, 무슨 일이었는지는 이 축이 보존한다.
 *
 * <p>‼️DSRE 원본에는 실수가 하나 있다 — 콤보에서 <b>회수(반품)도 'R' 로 저장</b>한다
 * ({@code FM_LOGI_MatInOut.cs:568-569}, 'B' 로 넣는 분기는 주석 처리돼 있다).
 * 그래서 운영 DSRE 데이터에서는 두 회수가 섞여 있다. 우리는 갈라서 저장한다 —
 * 지금 합쳐 두면 나중에 나눌 수 없다.
 */
public enum MaterialIo {

    /** 입고 — 인쇄소 등에서 들어온 물량. */
    INBOUND("입고", TxnType.INBOUND, 1),

    /** 회수(사고처리용) — 사고 처리로 되돌아온 물량. */
    RECOVER_ACCIDENT("회수(사고처리용)", TxnType.RETURN, 1),

    /** 회수(반품) — 반품으로 되돌아온 물량. */
    RECOVER_RETURN("회수(반품)", TxnType.RETURN, 1),

    /** 출고 — 시행·작업으로 나간 물량. <b>자재가 줄어드는 주 경로다.</b> */
    OUTBOUND("출고", TxnType.OUTBOUND, -1),

    /** 폐기 — 쓰지 못하게 되어 버린 물량. */
    DISPOSE("폐기", TxnType.DISPOSE, -1),

    /** 파손 — 훼손되어 못 쓰게 된 물량. 폐기와 잔량 효과는 같지만 보고가 다르다. */
    DAMAGE("파손", TxnType.DISPOSE, -1);

    private final String label;
    private final TxnType txnType;
    private final int sign;

    MaterialIo(String label, TxnType txnType, int sign) {
        this.label = label;
        this.txnType = txnType;
        this.sign = sign;
    }

    /**
     * 코드명·한글 <b>둘 다</b> 받는다. 화면은 목록에서 고른 한글을 그대로 되보낸다
     * (자재구분·출고유형에서 이미 같은 이유로 깨진 적이 있다).
     */
    @com.fasterxml.jackson.annotation.JsonCreator
    public static MaterialIo from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim();
        for (MaterialIo io : values()) {
            if (io.name().equals(v) || io.label.equals(v)) {
                return io;
            }
        }
        throw new IllegalArgumentException("알 수 없는 자재 입출고 구분입니다: " + raw
                + " (입고/회수(사고처리용)/회수(반품)/출고/폐기/파손)");
    }

    /** 화면 표기. 담당자는 OUTBOUND 가 아니라 '출고'라고 읽는다. */
    public String label() {
        return label;
    }

    /** 재고 원장에 남길 이벤트 종류. 잔량 계산은 이쪽이 한다. */
    public TxnType txnType() {
        return txnType;
    }

    /** 잔량 부호(+1 들어옴 / −1 나감). 화면은 언제나 <b>양수</b>로 입력받고 여기서 부호를 붙인다. */
    public int sign() {
        return sign;
    }

    /** 잔량이 줄어드는 구분인지. */
    public boolean isOutgoing() {
        return sign < 0;
    }
}
