package com.daesung.sales.common.mail;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * 메일 발송. 근거: 9/27 회의 A-4(항목 13·17) —
 * "이메일 기능 누락 — 통합매출조회·외상매출장조회 이메일 버튼 없음 → 추가(기존 매출프로그램에 있는 기능)".
 *
 * <p>레거시 {@code Common.vb:SendMail(수신1, 수신2, 제목, 본문, 첨부)} 를 그대로 옮긴 것이다.
 * 수신자 둘·참조에 발신자·첨부 하나까지 같은 모양이다.
 *
 * <p>★<b>레거시와 하나만 다르게 한다 — 계정을 소스에 두지 않는다.</b>
 * {@code Common.vb} 에는 SMTP 서버·계정·비밀번호가 문자열로 박혀 있었다. 소스를 받은 사람은
 * 누구나 회사 메일로 발송할 수 있다는 뜻이다. 같은 값을 쓰더라도 환경변수로만 받는다.
 *
 * <p>★<b>실패를 삼키지 않는다.</b> 레거시는 {@code MsgBox} 를 띄우고 {@code False} 를 돌려줬다.
 * 우리는 건별 결과({@link MailResult})를 돌려준다 — 50건 일괄 발송에서 3건이 실패했을 때
 * 어느 거래처인지 알 수 없으면 전부 다시 보내는 수밖에 없다.
 */
@Service
@RequiredArgsConstructor
public class MailService {

    private final MailProperties props;
    /** 설정이 없으면 빈이 만들어지지 않을 수 있어 선택 주입한다. */
    private final org.springframework.beans.factory.ObjectProvider<JavaMailSender> senderProvider;

    /** 발송 결과 한 건. */
    public record MailResult(String to, boolean sent, String message) {
    }

    /** 지금 보낼 수 있는 상태인지. 화면이 버튼을 감출 때 쓴다. */
    public boolean usable() {
        return props.usable() && senderProvider.getIfAvailable() != null;
    }

    /**
     * 메일 한 통.
     *
     * @param to1 받는 사람(필수)
     * @param to2 받는 사람 2(선택 — 거래처 이메일2). 레거시도 둘까지만 받는다
     */
    public MailResult send(String to1, String to2, String subject, List<MailAttachment> attachments) {
        String primary = blankToNull(to1);
        if (primary == null) {
            // ★"이메일이 없다"는 실패와 다르다. 거래처 마스터에 주소가 안 채워진 것이고,
            //   담당자가 고칠 곳은 메일 설정이 아니라 거래처관리 화면이다.
            return new MailResult(null, false, "거래처에 등록된 이메일이 없습니다.");
        }
        if (!props.usable()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "메일 발송이 꺼져 있거나 발신 주소가 설정되지 않았습니다. "
                            + "서버 설정(daesung.mail.enabled·from)을 확인하세요.");
        }
        JavaMailSender sender = senderProvider.getIfAvailable();
        if (sender == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "메일 서버가 설정되지 않았습니다(spring.mail.host).");
        }

        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from());
            helper.setTo(primary);
            String secondary = blankToNull(to2);
            if (secondary != null) {
                helper.setCc(secondary);
            }
            if (props.ccSender()) {
                // 레거시와 같다 — 보낸 기록이 발신 계정 메일함에 남는다.
                helper.addCc(props.from());
            }
            helper.setSubject(subject);
            helper.setText(props.body() == null ? "" : props.body(), false);

            for (MailAttachment a : attachments) {
                helper.addAttachment(a.filename(), new ByteArrayResource(a.content()));
            }
            sender.send(message);
            return new MailResult(primary, true, null);
        } catch (jakarta.mail.MessagingException | UnsupportedEncodingException
                 | org.springframework.mail.MailException e) {
            // ‼️한 건 실패로 일괄 발송 전체를 중단하지 않는다. 어느 거래처가 왜 실패했는지 남긴다.
            //   ★catch (Exception) 으로 묶지 않는다 — 정적분석이 잡기도 하지만,
            //     그러면 우리 코드의 NPE 같은 버그까지 "메일 실패"로 보고돼 원인이 묻힌다.
            //     받아야 할 것은 메일 계통 실패뿐이다(형식·인증·연결).
            return new MailResult(primary, false, e.getMessage());
        }
    }

    private InternetAddress from() throws UnsupportedEncodingException {
        String name = (props.fromName() == null || props.fromName().isBlank())
                ? props.from() : props.fromName();
        return new InternetAddress(props.from(), name, StandardCharsets.UTF_8.name());
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /** 첨부 하나짜리 편의 메서드. */
    public MailResult send(String to1, String to2, String subject, MailAttachment attachment) {
        List<MailAttachment> list = new ArrayList<>(1);
        list.add(attachment);
        return send(to1, to2, subject, list);
    }
}
