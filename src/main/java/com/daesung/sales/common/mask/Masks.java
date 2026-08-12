package com.daesung.sales.common.mask;

/**
 * 민감정보 마스킹. 근거: 요구사항 30p 기술검증란 {@code DB-73}
 * "사업자주민번호 등 민감정보 포함 — 암호화·마스킹 대상".
 *
 * <p><b>왜 백엔드에서 가리는가</b>: 화면에서만 가리면 엑셀 다운로드·API 직접 호출로 그대로
 * 빠져나간다(거래처 엑셀에 '사업자주민번호' 컬럼이 있다). 나가는 지점 한 곳에서 막는다.
 */
public final class Masks {

    private Masks() {
    }

    /** 마스킹된 값인지 판별하는 문자. 저장 시 이 문자가 섞여 오면 원본을 덮어쓰지 않는다. */
    public static final char MASK_CHAR = '*';

    /**
     * 주민등록번호 마스킹 — 앞 7자리(생년월일 + 성별)만 남기고 뒤를 가린다.
     *
     * <pre>
     *   800101-1234567 → 800101-1******
     *   8001011234567  → 800101-1******
     * </pre>
     *
     * <p>앞 7자리를 남기는 이유: 실무에서 동명이인 구분·연령 확인에 쓰이고, 뒤 6자리가 없으면
     * 그 자체로 신원을 특정하지 못한다. 형식이 다른 값(외국인·사업자 등록번호 등)은
     * 길이만 보고 뒤 절반을 가린다.
     */
    public static String residentNo(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() >= 8) {
            return digits.substring(0, 6) + "-" + digits.charAt(6) + "******";
        }
        // 자릿수가 모자라면 형식을 단정하지 않고 뒤 절반만 가린다(원본을 그대로 흘리지 않는 게 우선).
        String s = raw.trim();
        int keep = s.length() / 2;
        return s.substring(0, keep) + String.valueOf(MASK_CHAR).repeat(s.length() - keep);
    }

    /**
     * 이미 마스킹된 값인지. 수정 요청이 화면에 보이던 마스킹 값을 그대로 되보낼 수 있어,
     * 그걸 저장하면 원본이 {@code 800101-1******} 로 파괴된다. 저장 전에 이 검사로 걸러낸다.
     */
    public static boolean isMasked(String value) {
        return value != null && value.indexOf(MASK_CHAR) >= 0;
    }
}
