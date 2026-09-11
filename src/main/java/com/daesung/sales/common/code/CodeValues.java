package com.daesung.sales.common.code;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import java.util.Locale;

/**
 * 정해진 값만 들어오게 하는 <b>코드값 정규화</b> 공용 도구.
 *
 * <p>★왜 필요한가 — 자유 입력 문자열이 집계 축으로 쓰이면 <b>오타가 조용히 새 축을 만든다.</b>
 * 실제로 학년이 {@code 고3} 9건 · {@code 3} 1건으로 갈려 있었다. 두 값은 필터에서 서로 안 잡혀
 * 리포트가 두 줄로 쪼개지는데, 오류가 아니라서 아무도 모른다.
 *
 * <p>★화면이 셀렉트로 막는 것만으로는 부족하다. API를 직접 부르면 그대로 뚫린다.
 * 저장 직전에 서버가 한 번 더 좁혀야 같은 값만 남는다.
 *
 * <p>정책 두 가지
 * <ul>
 *   <li><b>받는 건 너그럽게</b> — {@code 고3}·{@code 3학년}·{@code 3}을 모두 {@code 3}으로 정규화한다.
 *       기존 데이터·엑셀 업로드·외부 연동이 표기를 조금씩 달리 쓰기 때문이다.</li>
 *   <li><b>저장은 하나로</b> — 정규화에 실패하면 <b>400으로 거부</b>한다.
 *       조용히 넘기면 "설정했는데 왜 안 걸리지"가 되고, 원인을 찾을 단서가 남지 않는다.</li>
 * </ul>
 */
public final class CodeValues {

    private CodeValues() {
    }

    /** 비교용 정규화 — 공백 제거 + 대문자. 로케일을 명시한다(터키어 i→İ 회피). */
    public static String norm(String v) {
        return (v == null) ? null : v.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    /**
     * 후보 중 하나로 좁힌다. null·공백은 <b>그대로 null</b>(선택 항목이라 "안 보냈다"는 뜻).
     *
     * @param label  오류 문구에 쓸 항목명(예: "학년")
     * @param raw    들어온 값
     * @param codes  허용 코드값(저장될 값)
     * @param aliases 코드값과 같은 뜻으로 받아줄 표기. {@code {"3", "고3"}} 처럼 {코드, 별칭...}
     */
    public static String resolve(String label, String raw, java.util.List<String> codes, String[][] aliases) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String n = norm(raw);
        for (String c : codes) {
            if (n.equals(norm(c))) {
                return c;
            }
        }
        for (String[] row : aliases) {
            for (int i = 1; i < row.length; i++) {
                if (n.equals(norm(row[i]))) {
                    return row[0];
                }
            }
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT,
                label + " 값을 알 수 없습니다: " + raw + " (가능: " + String.join(" / ", codes) + ")");
    }
}
