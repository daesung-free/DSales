package com.daesung.sales.common.idempotency;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;

/**
 * 마스터 동시 수정 충돌 검사(낙관적 잠금).
 *
 * <p>‼️엔티티에 {@code @Version} 컬럼만 두는 것으로는 아무것도 못 막는다 —
 * 요청마다 새로 읽으면 버전이 늘 일치하기 때문이다. <b>클라이언트가 읽을 때 받은 버전을
 * 되돌려 보내야</b> 그 사이 남이 고쳤는지 알 수 있다.
 *
 * <p>버전을 안 보내면 통과시킨다 — 기존 호출을 깨지 않기 위해서다.
 * 보내는 화면만 보호받는다는 뜻이고, 그게 지금 할 수 있는 최선이다.
 */
public final class VersionGuard {

    private VersionGuard() {
    }

    public static void check(Long expected, Long actual, String what) {
        if (expected == null) {
            return;
        }
        if (!expected.equals(actual)) {
            throw new BusinessException(ErrorCode.DUPLICATE_REQUEST,
                    what + "을(를) 그 사이 다른 사람이 수정했습니다. "
                            + "새로고침해서 바뀐 내용을 확인한 뒤 다시 저장해 주세요.");
        }
    }
}
