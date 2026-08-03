package com.daesung.sales.batch.entity;

import com.daesung.sales.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 알림 적재. 25p 담보만기 팝업(개발문서 FE A11)이 읽어가는 원천.
 *
 * <p>메일 발송(SMTP)은 인프라 확정 전이라 배치는 알림을 <b>쌓기만</b> 한다.
 * 발송 채널은 이 테이블을 읽는 형태로 나중에 붙이면 되고, 그동안에도 팝업은 동작한다.
 * {@code dedupKey}로 중복을 막아 배치를 몇 번 돌려도 같은 알림이 늘지 않는다.
 */
@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(name = "dedup_key", nullable = false, length = 150)
    private String dedupKey;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 500)
    private String message;

    @Column(name = "ref_type", length = 30)
    private String refType;

    @Column(name = "ref_id")
    private Long refId;

    @Column(name = "notify_date", nullable = false)
    private LocalDate notifyDate;

    @Column(name = "read_yn", nullable = false)
    private boolean readYn;

    public static Notification of(String type, String dedupKey, String title, String message,
                                  String refType, Long refId, LocalDate notifyDate) {
        Notification n = new Notification();
        n.type = type;
        n.dedupKey = dedupKey;
        n.title = title;
        n.message = message;
        n.refType = refType;
        n.refId = refId;
        n.notifyDate = notifyDate;
        return n;
    }

    public void markRead() {
        this.readYn = true;
    }
}
