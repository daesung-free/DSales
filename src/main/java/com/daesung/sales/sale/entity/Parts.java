package com.daesung.sales.sale.entity;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import java.util.List;
import java.util.Map;

/**
 * 무상 세부구분(part) 값 — 레거시 {@code salesData.part}.
 *
 * <p>제품수불부 무상 4칸(IC학생용·IC·M+·기타)이 이 값으로 갈린다.
 * 값의 출처는 레거시 두 곳이다 — 수기 매출등록 그리드의 '구분' 컬럼({@code UC_TabPages.vb:738})과
 * IC 자동적재({@code LoadICData.vb}, 외부 Access 파일의 {@code kubun}).
 *
 * <p>★<b>모르는 값은 거부한다.</b> 자유 문자열로 두면 {@code M+}/{@code M＋}/{@code 엠플러스}가
 * 섞여 집계가 조용히 쪼개진다 — 오류가 안 나서 아무도 모른다.
 * 거래처구분·학년에서 같은 이유로 이미 한 번 겪었다.
 *
 * <p>‼️{@code IC+}는 수불부 4칸 어디에도 들어가지 않고 <b>기타</b>로 떨어진다.
 * 레거시가 그렇다({@code 제품수불부.vb:121} — M+만 자기 칸이 있고 IC+는 없다).
 * 다른 화면들은 IC·M+·IC+를 늘 한 묶음으로 다루는데 수불부만 M+를 떼어냈다 —
 * 의도인지 누락인지 코드로는 알 수 없어 <b>레거시 그대로</b> 둔다(발주처 확인 대상).
 */
public final class Parts {

    /** 학생용 — IC 학생 몫. */
    public static final String STUDENT = "학생용";
    /** 교사용 — 선생님 몫. tradeClass(대분류)가 교재냐 IC냐로 수불부 칸이 갈린다. */
    public static final String TEACHER = "교사용";
    /** M+ — IC 계열 상품군. 수불부에 자기 칸이 있다. */
    public static final String M_PLUS = "M+";
    /** IC+ — IC 계열 상품군. ‼️수불부에서는 '기타'로 떨어진다(레거시 그대로). */
    public static final String IC_PLUS = "IC+";

    private static final Map<String, String> ALIAS = Map.of(
            STUDENT, STUDENT,
            TEACHER, TEACHER,
            M_PLUS, M_PLUS,
            IC_PLUS, IC_PLUS,
            // 전각 +. 담당자가 한글 입력 상태로 치면 이렇게 들어온다.
            "M＋", M_PLUS,
            "IC＋", IC_PLUS);

    private Parts() {
    }

    /** 허용 값 목록(화면 셀렉트용). */
    public static List<String> values() {
        return List.of(STUDENT, TEACHER, M_PLUS, IC_PLUS);
    }

    /**
     * 정규화. 빈 값은 null(= 미지정)로 둔다 — 무상이 아닌 건에는 원래 안 붙는 값이다.
     *
     * @throws BusinessException 아는 값이 아니면 INVALID_INPUT
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = ALIAS.get(raw.trim());
        if (v == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "알 수 없는 무상 세부구분입니다: " + raw + " (학생용/교사용/M+/IC+)");
        }
        return v;
    }
}
