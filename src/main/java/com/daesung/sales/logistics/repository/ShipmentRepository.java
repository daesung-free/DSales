package com.daesung.sales.logistics.repository;

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
     */
    @Query("""
            select s from Shipment s join fetch s.partner p
             where s.tradeDate between :from and :to
               and (:tradeClass is null or s.tradeClass = :tradeClass)
               and (:partnerId is null or p.id = :partnerId)
               and (:printed is null
                    or (:printed = true  and s.printedAt is not null)
                    or (:printed = false and s.printedAt is null))
             order by s.tradeDate desc, p.code, s.schoolCode
            """)
    List<Shipment> search(@Param("from") LocalDate from, @Param("to") LocalDate to,
                          @Param("tradeClass") String tradeClass,
                          @Param("partnerId") Long partnerId,
                          @Param("printed") Boolean printed);
}
