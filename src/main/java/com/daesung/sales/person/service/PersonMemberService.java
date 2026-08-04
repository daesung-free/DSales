package com.daesung.sales.person.service;

import com.daesung.sales.common.audit.CurrentAuditor;
import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.common.response.PageResponse;
import com.daesung.sales.person.dto.PersonMemberRequest;
import com.daesung.sales.person.dto.PersonMemberResponse;
import com.daesung.sales.person.entity.PersonMember;
import com.daesung.sales.person.repository.PersonMemberRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 개인회원관리(구 IC). 근거: 레거시 개인회원관리.vb + DSLab.personData(실데이터 145행). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PersonMemberService {

    private final PersonMemberRepository repository;
    private final CurrentAuditor currentAuditor;

    public PageResponse<PersonMemberResponse> search(LocalDate from, LocalDate to, String keyword,
                                                     String goods, String tel, Pageable pageable) {
        LocalDateTime fromAt = (from == null) ? null : from.atStartOfDay();
        LocalDateTime toAt = (to == null) ? null : to.atTime(LocalTime.MAX);
        return PageResponse.of(repository
                .search(fromAt, toAt, blankToNull(keyword), blankToNull(goods), blankToNull(tel), pageable)
                .map(PersonMemberResponse::from));
    }

    public PersonMemberResponse findById(Long id) {
        return PersonMemberResponse.from(getOrThrow(id));
    }

    @Transactional
    public PersonMemberResponse create(PersonMemberRequest req) {
        PersonMember m = PersonMember.create(req.fiscalYear(), null, req.payDate(),
                req.studentId(), req.studentName(), req.goodsCode(), req.goodsName(),
                req.post(), req.addr1(), req.addr2(), req.receiver(),
                req.tel1(), req.tel2(), req.memo(), req.manager());
        return PersonMemberResponse.from(repository.save(m));
    }

    @Transactional
    public PersonMemberResponse update(Long id, PersonMemberRequest req) {
        PersonMember m = getOrThrow(id);
        m.update(req.fiscalYear(), req.payDate(), req.studentId(), req.studentName(),
                req.goodsCode(), req.goodsName(), req.post(), req.addr1(), req.addr2(),
                req.receiver(), req.tel1(), req.tel2(), req.memo(), req.manager());
        return PersonMemberResponse.from(m);
    }

    /** 삭제(논리삭제). 레거시는 물리 DELETE였으나 게이트규칙에 따라 행을 남긴다. */
    @Transactional
    public void delete(Long id) {
        getOrThrow(id).markDeleted(currentAuditor.username());
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private PersonMember getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "개인회원이 없습니다. id=" + id));
    }
}
