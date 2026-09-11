package com.daesung.sales.partner.repository;

import com.daesung.sales.partner.entity.Partner;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PartnerRepository extends JpaRepository<Partner, Long> {
    Optional<Partner> findByCode(String code);

    Page<Partner> findByCodeContainingIgnoreCaseOrNameContainingIgnoreCase(
            String code, String name, Pageable pageable);

    /** 담보 만기일이 기준일(threshold) 이하인 거래처(만료+임박). 만기일 오름차순. null 만기일은 자동 제외. */
    List<Partner> findByAssureExpiryLessThanEqualOrderByAssureExpiryAsc(LocalDate threshold);

    /**
     * 거래처 검색. 키워드(코드·거래처명 부분일치) + 만료 포함 여부.
     * includeExpired=false면 거래중(end_date IS NULL)만 — 레거시 거래처관리 기본 동작.
     */
    @org.springframework.data.jpa.repository.Query("""
            select p from Partner p
             where (:keyword is null
                    or lower(p.code) like lower(concat('%', :keyword, '%'))
                    or lower(p.name) like lower(concat('%', :keyword, '%')))
               and (:includeExpired = true or p.endDate is null)
             order by p.code
            """)
    org.springframework.data.domain.Page<Partner> search(
            @org.springframework.data.repository.query.Param("keyword") String keyword,
            @org.springframework.data.repository.query.Param("includeExpired") boolean includeExpired,
            org.springframework.data.domain.Pageable pageable);

    /**
     * 쓰이고 있는 지역·관할지역 목록(거래처 등록 화면 선택용).
     *
     * <p>★분류코드와 같은 이유다 — 지역은 조회 필터({@code ?region=})로 쓰이는데 자유 입력이라
     * {@code 경남}과 {@code 경상남도}가 섞이면 한쪽이 필터에서 통째로 빠진다.
     * 값이 정해져 있지 않아 enum으로 못 박을 수 없으니, <b>쓰이는 값을 보여줘 고르게</b> 한다.
     *
     * <p>{@code kind}로 축을 가른다 — 지역(region)과 관할지역(zone2)은 <b>별개 축</b>이다
     * (정본 4탭 거래처관리에 둘 다 있다). 한 목록에 섞으면 어느 칸에 넣을 값인지 알 수 없다.
     */
    @Query("""
            select p.region as value, count(p) as usedCount
            from Partner p
            where p.region is not null and p.region <> ''
            group by p.region
            order by count(p) desc, p.region asc
            """)
    List<CodeUsage> findUsedRegions();

    @Query("""
            select p.zone2 as value, count(p) as usedCount
            from Partner p
            where p.zone2 is not null and p.zone2 <> ''
            group by p.zone2
            order by count(p) desc, p.zone2 asc
            """)
    List<CodeUsage> findUsedZones();

    /** 위 두 조회의 투영. 건수가 1인 값은 오타를 의심할 자리다. */
    interface CodeUsage {
        String getValue();

        long getUsedCount();
    }

}
