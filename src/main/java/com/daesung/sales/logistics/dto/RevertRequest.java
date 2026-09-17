package com.daesung.sales.logistics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 출력·확인 표시를 되돌릴 때의 사유(B-14).
 *
 * <p>‼️<b>사유를 선택으로 두지 않는다.</b> 되돌리기는 "언제 처음 작업지시가 나갔나"를 지우는
 * 행위다. 사유 없이 내릴 수 있으면 나중에 "이 건은 왜 지시가 안 나간 걸로 되어 있나"에
 * 아무도 답할 수 없고, 그때 담당자는 출력 표시 자체를 못 믿게 된다.
 * 사유는 {@code status_history}에 누가·언제와 함께 남는다.
 */
@Schema(name = "RevertRequest", description = "되돌리기 사유")
public record RevertRequest(

        @Schema(description = "되돌리는 사유", example = "수량 오기로 재출력 필요",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "되돌리는 사유를 입력하세요.")
        @Size(max = 500)
        String reason
) {
}
