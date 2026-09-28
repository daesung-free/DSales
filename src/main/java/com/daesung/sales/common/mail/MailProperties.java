package com.daesung.sales.common.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 메일 발송 설정(9/27 회의 항목 17·A-4).
 *
 * <p>‼️<b>SMTP 계정·비밀번호는 여기 없다.</b> 그건 {@code spring.mail.*} 이 환경변수로 받는다.
 * 레거시({@code Common.vb:SendMail})는 서버·계정·비밀번호를 전부 소스에 박아 뒀는데,
 * 그건 소스를 받은 사람 누구나 회사 메일로 발송할 수 있다는 뜻이다. 같은 값을 쓰더라도
 * <b>저장소에는 두지 않는다</b>.
 *
 * @param enabled  기본 false. ‼️설정이 빈 채로 켜 두면 '전송'을 눌러도 조용히 실패하고
 *                 담당자는 보냈다고 믿는다. 값을 넣은 환경에서만 켠다.
 * @param from     보내는 사람 주소(보통 SMTP 계정과 같다)
 * @param fromName 보내는 사람 표시 이름
 * @param body     본문 문구. 레거시 {@code 조회.vb:3781} 그대로
 * @param ccSender 참조에 발신자를 넣을지. 레거시는 항상 넣었다 — 보낸 기록을 메일함에 남기려는 것
 */
@ConfigurationProperties(prefix = "daesung.mail")
public record MailProperties(
        boolean enabled,
        String from,
        String fromName,
        String body,
        boolean ccSender
) {
    /** 보낼 수 있는 상태인지. 켜져 있고 발신 주소가 있어야 한다. */
    public boolean usable() {
        return enabled && from != null && !from.isBlank();
    }
}
