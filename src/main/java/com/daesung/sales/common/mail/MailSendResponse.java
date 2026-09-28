package com.daesung.sales.common.mail;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 일괄 메일 발송 결과.
 *
 * <p>★<b>건별로 돌려준다.</b> 레거시는 성공·실패 건수만 세고 상세는 로그 파일에 적었다
 * ({@code 외상매출장조회.vb:1434}). 50건 중 3건이 실패했을 때 어느 거래처인지 모르면
 * 전부 다시 보내는 수밖에 없다.
 */
@Schema(name = "MailSendResponse", description = "메일 일괄 발송 결과")
public record MailSendResponse(

        @Schema(description = "보낸 건수") int sent,
        @Schema(description = "실패 건수. 0이 아니면 lines 에서 사유를 볼 것") int failed,
        @Schema(description = "이메일이 없어 건너뛴 거래처 수 — 실패와 다르다. 거래처관리에서 채워야 한다")
        int skipped,
        @Schema(description = "거래처별 결과") List<Line> lines
) {
    @Schema(name = "MailSendLine")
    public record Line(
            @Schema(description = "거래처 id") Long partnerId,
            @Schema(description = "거래처코드") String partnerCode,
            @Schema(description = "거래처명") String partnerName,
            @Schema(description = "받는 사람(첫 주소)") String to,
            @Schema(description = "SENT / FAILED / NO_EMAIL") String result,
            @Schema(description = "실패·건너뜀 사유") String message
    ) {
    }
}
