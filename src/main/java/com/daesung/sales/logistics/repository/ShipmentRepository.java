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
}
