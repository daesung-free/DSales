package com.daesung.sales.common.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 같은 요청을 한 번만 처리하기 위한 기록. 키는 클라이언트가 만들어 보낸다. */
@Entity
@Table(name = "idempotency_record")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyRecord {

    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String DONE = "DONE";

    @Id
    @Column(name = "idem_key", length = 100)
    private String idemKey;

    @Column(nullable = false, length = 40)
    private String scope;

    @Column(nullable = false, length = 12)
    private String status;

    @Column(name = "response_json", columnDefinition = "MEDIUMTEXT")
    private String responseJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    public static IdempotencyRecord start(String key, String scope, String actor) {
        IdempotencyRecord r = new IdempotencyRecord();
        r.idemKey = key;
        r.scope = scope;
        r.status = IN_PROGRESS;
        r.createdAt = LocalDateTime.now();
        r.createdBy = actor;
        return r;
    }

    public void complete(String json) {
        this.responseJson = json;
        this.status = DONE;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isDone() {
        return DONE.equals(status);
    }
}
