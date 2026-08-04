package com.daesung.sales.person.repository;

import com.daesung.sales.person.entity.PersonMember;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonMemberRepository extends JpaRepository<PersonMember, Long> {

    /**
     * 레거시 개인회원관리.vb 조회 조건을 그대로 옮긴 검색.
     * 결제일 기간 + (학생ID|학생이름) + 상품명 + (연락처1|연락처2) 부분일치.
     * 각 조건은 미입력 시 무시된다(레거시는 빈 문자열 like '%%'로 같은 효과).
     */
    @Query("""
            select m from PersonMember m
             where (:from is null or m.payDate >= :from)
               and (:to is null or m.payDate <= :to)
               and (:keyword is null or lower(m.studentId) like lower(concat('%', :keyword, '%'))
                                     or lower(m.studentName) like lower(concat('%', :keyword, '%')))
               and (:goods is null or lower(m.goodsName) like lower(concat('%', :goods, '%')))
               and (:tel is null or m.tel1 like concat('%', :tel, '%')
                                 or m.tel2 like concat('%', :tel, '%'))
             order by m.inputDate desc, m.studentId asc
            """)
    Page<PersonMember> search(@Param("from") LocalDateTime from,
                              @Param("to") LocalDateTime to,
                              @Param("keyword") String keyword,
                              @Param("goods") String goods,
                              @Param("tel") String tel,
                              Pageable pageable);
}
