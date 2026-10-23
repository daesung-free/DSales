package com.daesung.sales.partner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 거래처 ↔ <b>DSRE 거래처코드</b> 매핑(1:N).
 *
 * <p>★<b>1:1 이 아니다.</b> 운영 DSRE 214행 실측(2026-10-23)에서 매출코드 하나에
 * DSRE 코드가 여럿 달린 거래처가 5곳 나왔다(한 곳은 다섯 개).
 * 한 칸으로 두면 동기화가 돌 때마다 마지막 값으로 덮이고(비멱등),
 * 고르지 않은 코드를 쓰는 <b>학교가 통째로 안 붙는다</b>(최소값 채택 시 145곳).
 *
 * <p>학교 조인은 이 표를 탄다. {@code partners.dsre_code} 는
 * PDF 파일명처럼 <b>하나만 써야 하는 자리</b>의 대표값으로 남는다.
 */
@Entity
@Table(name = "partner_dsre_code")
@IdClass(PartnerDsreCode.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PartnerDsreCode {

    @Id
    @Column(name = "partner_id")
    private Long partnerId;

    @Id
    @Column(name = "dsre_code", length = 20)
    private String dsreCode;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static PartnerDsreCode of(Long partnerId, String dsreCode) {
        PartnerDsreCode m = new PartnerDsreCode();
        m.partnerId = partnerId;
        m.dsreCode = dsreCode;
        m.createdAt = LocalDateTime.now();
        return m;
    }

    /**
     * 복합키.
     *
     * <p>‼️필드는 <b>JPA가 리플렉션으로 채운다</b> — 우리 코드에 대입이 없다.
     * 정적분석이 "한 번도 안 쓰인 필드"로 보고 {@code UWF_UNWRITTEN_FIELD} 를 내길래
     * <b>생성자를 열어</b> 실제로 대입되게 했다(규칙을 끄지 않는다).
     * 기본 생성자는 JPA 가 요구해서 남긴다.
     */
    public static class Key implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long partnerId;
        private String dsreCode;

        public Key() {
        }

        public Key(Long partnerId, String dsreCode) {
            this.partnerId = partnerId;
            this.dsreCode = dsreCode;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key k)) {
                return false;
            }
            return Objects.equals(partnerId, k.partnerId) && Objects.equals(dsreCode, k.dsreCode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(partnerId, dsreCode);
        }
    }
}
