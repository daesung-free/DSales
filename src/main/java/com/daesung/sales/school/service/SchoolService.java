package com.daesung.sales.school.service;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.common.response.PageResponse;
import com.daesung.sales.school.dto.SchoolCreateRequest;
import com.daesung.sales.school.dto.SchoolResponse;
import com.daesung.sales.school.dto.SchoolUpdateRequest;
import com.daesung.sales.school.entity.School;
import com.daesung.sales.school.repository.SchoolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 학교/학원 마스터(35p). DSRE '가져오기'(동기화)는 후속(조건부). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchoolService {

    private final SchoolRepository schoolRepository;

    public PageResponse<SchoolResponse> findAll(String keyword, Pageable pageable) {
        Page<School> page = (keyword == null || keyword.isBlank())
                ? schoolRepository.findAll(pageable)
                : schoolRepository.findBySchoolCodeContainingIgnoreCaseOrSchoolNameContainingIgnoreCase(
                        keyword, keyword, pageable);
        return PageResponse.of(page.map(SchoolResponse::from));
    }

    public SchoolResponse findById(Long id) {
        return SchoolResponse.from(getOrThrow(id));
    }

    @Transactional
    public SchoolResponse create(SchoolCreateRequest req) {
        schoolRepository.findBySchoolCode(req.schoolCode()).ifPresent(s -> {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 존재하는 학교코드: " + req.schoolCode());
        });
        School school = School.create(req.schoolCode(), req.custCode(), req.custName(), req.city(), req.region(),
                req.schoolName(), req.isSchool() == null || req.isSchool(), req.schoolType(),
                req.clientCategory(), req.memo());
        return SchoolResponse.from(schoolRepository.save(school));
    }

    @Transactional
    public SchoolResponse update(Long id, SchoolUpdateRequest req) {
        School school = getOrThrow(id);
        school.update(req.custCode(), req.custName(), req.city(), req.region(), req.schoolName(),
                req.isSchool() == null || req.isSchool(), req.schoolType(), req.clientCategory(), req.memo());
        return SchoolResponse.from(school);
    }

    private School getOrThrow(Long id) {
        return schoolRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "학교가 없습니다. id=" + id));
    }
}
