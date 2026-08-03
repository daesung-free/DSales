package com.daesung.sales.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 논리삭제(soft delete) 표준 베이스. 감사컬럼({@link BaseEntity}) + 삭제자·삭제시각.
 *
 * <p>게이트규칙 "논리삭제 표준 — 물리 DELETE는 예외 승인 절차로만"의 구현체다.
 * 삭제해도 행은 남고 <b>누가·언제</b> 지웠는지 추적된다(보안심사·감사 대응).
 *
 * <p>상속 엔티티는 조회에서 삭제행이 자동 제외되도록 {@code @SQLRestriction("deleted_at is null")}를
 * 함께 붙인다. 단, <b>거래 기록이 FK로 참조하는 마스터에는 붙이지 말 것</b> — 삭제된 마스터를
 * 물고 있는 과거 매출·재고의 연관 로딩이 깨진다(그 경우 목록 쿼리에서 명시 필터).
 *
 * <p>DDL은 Flyway {@code V25__soft_delete.sql}가 소유한다. 유니크 키에는 {@code del_key}
 * 생성컬럼이 함께 들어가 있어 "지웠다가 같은 코드로 재등록"이 막히지 않는다.
 */
@Getter
@MappedSuperclass
public abstract class SoftDeletableEntity extends BaseEntity {

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by", length = 50)
    private String deletedBy;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * 논리삭제 표시. 이미 삭제된 행은 최초 삭제 기록을 보존한다(재삭제로 삭제자가 덮이지 않게).
     *
     * @param actor 삭제 주체(현재 로그인 사용자). {@code CurrentAuditor.username()} 사용.
     */
    public void markDeleted(String actor) {
        if (deletedAt != null) {
            return;
        }
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = actor;
    }
}
