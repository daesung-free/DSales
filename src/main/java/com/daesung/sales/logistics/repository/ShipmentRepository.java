package com.daesung.sales.logistics.repository;

import com.daesung.sales.logistics.entity.DeliveryType;
import com.daesung.sales.logistics.entity.Shipment;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    /**
     * 그 <b>신청번호(DSRE reqCd)</b>로 세운 매출이 묶인 발송 건들.
     *
     * <p>근거: 프론트 실측 지적(2026-10-01, 2026-10-08 [A]5) — 「발송취소가 실제로 상태를
     * 되돌리지 못한다」. 원인은 <b>축이 둘</b>이었다. 발송취소는 DSRE {@code STATE} 를 D→W 로
     * 되돌리는데, 작업요청서 화면의 '발송완료'는 <b>우리 {@code shipment.sent_date}</b> 로
     * 판정한다 — DSRE 는 돌아갔는데 우리 화면은 계속 발송완료로 보였다.
     *
     * <p>발송 건은 (거래일자·거래처·학교·분류)로 묶이므로, 신청번호에서 그 키를 거쳐 되짚는다.
     * ★취소된 매출은 제외한다 — 되돌린 건의 신청번호로 남의 발송 건을 내리면 안 된다.
     */
    @Query("""
            select distinct sh from Shipment sh
             where exists (select 1 from Sale sa join sa.product p
                            where sa.reqCd = :reqCd and sa.canceled = false
                              and sa.salesDate = sh.tradeDate
                              and sa.partner.id = sh.partner.id
                              and coalesce(sa.schoolCode, '') = coalesce(sh.schoolCode, '')
                              and coalesce(p.salesDivision, '') = coalesce(sh.tradeClass, ''))
            """)
    List<Shipment> findByReqCd(@Param("reqCd") Integer reqCd);

    /** 같은 (일자·거래처·학교·분류)의 발송 건. 매출을 여러 번 등록해도 한 건에 묶는다. */
    @Query("""
            select s from Shipment s
             where s.tradeDate = :tradeDate and s.partner.id = :partnerId
               and ((:schoolCode is null and s.schoolCode is null) or s.schoolCode = :schoolCode)
               and ((:tradeClass is null and s.tradeClass is null) or s.tradeClass = :tradeClass)
            """)
    Optional<Shipment> findKey(@Param("tradeDate") LocalDate tradeDate,
                               @Param("partnerId") Long partnerId,
                               @Param("schoolCode") String schoolCode,
                               @Param("tradeClass") String tradeClass);

    /**
     * 기간·조건 조회. printed: true=출력분만, false=미출력분만, null=전체
     * (레거시 작업요청서의 '출력 안 된 건만' 필터에 대응).
     * acknowledged 도 같은 규칙이다 — 확인은 출력 다음 단계라 "출력됐는데 아직 확인 안 된 건"을
     * {@code printed=true &amp; acknowledged=false} 로 추릴 수 있어야 한다.
     */
    @Query("""
            select s from Shipment s join fetch s.partner p
             where s.tradeDate between :from and :to
               and (:tradeClass is null or s.tradeClass = :tradeClass)
               and (:partnerId is null or p.id = :partnerId)
               and (:printed is null
                    or (:printed = true  and s.printedAt is not null)
                    or (:printed = false and s.printedAt is null))
               and (:acknowledged is null
                    or (:acknowledged = true  and s.acknowledgedAt is not null)
                    or (:acknowledged = false and s.acknowledgedAt is null))
               and (:deliveryType is null or s.deliveryType = :deliveryType)
             order by s.tradeDate desc, p.code, s.schoolCode
            """)
    List<Shipment> search(@Param("from") LocalDate from, @Param("to") LocalDate to,
                          @Param("tradeClass") String tradeClass,
                          @Param("partnerId") Long partnerId,
                          @Param("printed") Boolean printed,
                          @Param("acknowledged") Boolean acknowledged,
                          @Param("deliveryType") DeliveryType deliveryType);

    /**
     * 미확인(미출력) 발송 건수. 대시보드 '미확인 출고요청' 카드와 메뉴 배지가 쓴다.
     *
     * <p>판정은 {@link #search} 의 {@code printed=false} 와 <b>같다</b>(printedAt is null).
     * 따로 세면 카드에는 3건인데 화면을 열면 5건인 상황이 생긴다.
     */
    @Query("""
            select count(s) from Shipment s
             where s.tradeDate between :from and :to
               and s.printedAt is null
            """)
    int countUnprinted(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 작업요청서 조회 — <b>삭제된 건까지</b>(9/27 회의 항목 20 ②).
     *
     * <p>★{@code @SQLRestriction} 때문에 JPQL 로는 삭제분을 볼 수 없다. 네이티브로 우회한다.
     *
     * <p>★<b>왜 삭제분을 보여줘야 하는가</b> — 레거시가 그렇게 한다.
     * 목록에서 지우지 않고 <b>취소선+회색</b>으로 남긴다({@code 작업요청서.vb:768}).
     * 숨겨 버리면 "취소된 건"과 "원래 없던 건"이 구분되지 않아, 담당자가 같은 발송을 다시 만든다.
     * 대신 출력·확인·발송 대상에서는 빠진다(레거시 377·890·1016행도 {@code Continue For}).
     */
    @Query(value = """
            SELECT s.* FROM shipment s
              JOIN partners p ON p.id = s.partner_id
             WHERE s.trade_date BETWEEN :from AND :to
               AND (:tradeClass IS NULL OR s.trade_class = :tradeClass)
               AND (CAST(:partnerId AS SIGNED) IS NULL OR p.id = :partnerId)
               AND (CAST(:printed AS SIGNED) IS NULL
                    OR (:printed = 1 AND s.printed_at IS NOT NULL)
                    OR (:printed = 0 AND s.printed_at IS NULL))
               AND (CAST(:acknowledged AS SIGNED) IS NULL
                    OR (:acknowledged = 1 AND s.acknowledged_at IS NOT NULL)
                    OR (:acknowledged = 0 AND s.acknowledged_at IS NULL))
               AND (:deliveryType IS NULL OR s.delivery_type = :deliveryType)
             ORDER BY s.trade_date DESC, p.code, s.school_code
            """, nativeQuery = true)
    List<Shipment> findAllIncludingDeleted(@Param("from") LocalDate from,
                                           @Param("to") LocalDate to,
                                           @Param("tradeClass") String tradeClass,
                                           @Param("partnerId") Long partnerId,
                                           @Param("printed") Integer printed,
                                           @Param("acknowledged") Integer acknowledged,
                                           @Param("deliveryType") String deliveryType);

    /** 삭제된 건 포함 단건 조회 — 삭제 대상을 찾을 때 쓴다(@SQLRestriction 우회). */
    @Query(value = "SELECT * FROM shipment WHERE id = :id", nativeQuery = true)
    Optional<Shipment> findByIdIncludingDeleted(@Param("id") Long id);
}
