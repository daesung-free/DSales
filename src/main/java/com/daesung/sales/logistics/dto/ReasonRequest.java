package com.daesung.sales.logistics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 사유가 <b>선택</b>인 요청.
 *
 * <p>근거: 9/27 회의 A-3 — "'사유' 입력 제거 공통(9·12·20) —
 * 폐기 / 통합매출(취소·삭제) / 작업요청서에서 사유칸 제거".
 *
 * <p>★<b>{@link RevertRequest} 와 일부러 나눠 두었다.</b> 처음에는 RevertRequest 의
 * 사유를 선택으로 바꿨는데, 그 DTO를 <b>재고 전표 삭제</b>({@code DELETE /stock/vouchers/…})도
 * 함께 쓰고 있어 회의에서 언급되지 않은 화면까지 검증이 풀렸다(테스트가 잡았다).
 * 사유를 없애기로 한 화면과 그대로 둘 화면이 섞여 있으므로 타입으로 가른다 —
 * 한쪽을 고칠 때 다른 쪽이 조용히 따라 바뀌지 않게.
 *
 * <p>사유가 없어도 <b>이력은 남는다</b>. 값이 비면 "(사유 미입력)"으로 적어
 * "안 적은 것"과 "기록이 안 된 것"을 구분한다.
 */
@Schema(name = "ReasonRequest", description = "사유(선택). 본문 자체를 생략해도 된다")
public record ReasonRequest(

        @Schema(description = "사유(선택)", example = "수량 오기로 재출력 필요")
        @Size(max = 500)
        String reason
) {
    /** 본문 없이 호출하는 화면이 있어 null 요청을 그대로 받는다. */
    public static String reasonOf(ReasonRequest req) {
        return (req == null) ? null : req.reason();
    }
}
