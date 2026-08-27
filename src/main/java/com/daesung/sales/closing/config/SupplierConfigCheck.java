package com.daesung.sales.closing.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 공급자(자사) 정보 미설정 경고. 근거: 프론트 실호출 실측(갭리포트 §P-5) —
 * 계산서신고 응답의 {@code supplierName}·{@code supplierBizNo}가 받은 5건 전부 빈 값이었다.
 *
 * <p>★<b>왜 기동할 때 알려야 하나</b> — 이건 <b>조용히 틀리는</b> 종류다.
 * 필드는 응답에 있으니 스펙만 보면 정상이고, 화면도 빈 칸일 뿐 오류가 나지 않는다.
 * 그런데 홈택스 양식은 공급자 정보가 없으면 <b>제출 자체가 안 된다</b> —
 * 재무팀이 신고하려는 순간에야 드러나고, 그때는 이미 늦다.
 *
 * <p>기동을 막지는 않는다. 계산서 말고 다른 화면은 멀쩡히 돌아가야 하고,
 * 로컬 개발에서 매번 사업자번호를 넣게 하는 것도 과하다.
 * 대신 JWT 더미 시크릿과 같은 급으로 눈에 띄게 남긴다.
 */
@Component
@RequiredArgsConstructor
public class SupplierConfigCheck {

    private static final Logger log = LoggerFactory.getLogger(SupplierConfigCheck.class);

    private final SupplierProperties supplier;

    @EventListener(ApplicationReadyEvent.class)
    public void warnIfMissing() {
        if (supplier.isConfigured()) {
            return;
        }
        // ★설정값을 로그에 넣지 않는다 — 상수 문자열만 남긴다.
        //   ① 사업자번호는 로그에 남길 값이 아니고
        //   ② 설정값은 환경변수로 들어오는 외부 입력이라, 찍는 순간 로그 위조(CRLF) 대상이 된다.
        //   무엇을 채워야 하는지는 아래 문구에 이미 다 적혀 있어 값을 보여줄 이유가 없다.
        log.error("★★★ 공급자(자사) 정보 미설정(상호 또는 사업자번호) — "
                + "계산서·거래명세서의 공급자 칸이 빈 채로 나갑니다. "
                + "홈택스 양식은 이 값이 없으면 제출이 안 됩니다. "
                + "DAESUNG_SUPPLIER_NAME · DAESUNG_SUPPLIER_BIZ-NO 등을 환경변수로 주입하세요. "
                + "(발행 법인은 대성학력개발연구소 1개로 확정 — 정본 2탭) ★★★");
    }
}
