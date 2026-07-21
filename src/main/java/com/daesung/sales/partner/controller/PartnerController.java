package com.daesung.sales.partner.controller;

import com.daesung.sales.common.dto.PageRequestDto;
import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.common.response.PageResponse;
import com.daesung.sales.partner.dto.PartnerCreateRequest;
import com.daesung.sales.partner.dto.PartnerResponse;
import com.daesung.sales.partner.dto.PartnerUpdateRequest;
import com.daesung.sales.partner.service.PartnerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 기초관리 - 거래처 관리. 실제 경로: /api/v1/masters/clients. */
@Tag(name = "기초관리 · 거래처", description = "거래처 마스터 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/masters/clients")
public class PartnerController {

    private final PartnerService partnerService;

    @Operation(summary = "거래처 목록 조회", description = "keyword(코드/명 부분일치)로 검색, 페이징·정렬 지원")
    @GetMapping
    public ApiResponse<PageResponse<PartnerResponse>> list(
            @Parameter(description = "검색어(거래처코드 또는 거래처명 부분일치)") @RequestParam(required = false) String keyword,
            @ParameterObject PageRequestDto pageReq) {
        return ApiResponse.success(partnerService.findAll(keyword, pageReq.toPageable()));
    }

    @Operation(summary = "거래처 상세 조회", description = "id로 단건 조회. 없으면 404")
    @GetMapping("/{id}")
    public ApiResponse<PartnerResponse> get(@PathVariable Long id) {
        return ApiResponse.success(partnerService.findById(id));
    }

    @Operation(summary = "거래처 등록", description = "거래처코드 중복 시 400 반환")
    @PostMapping
    public ApiResponse<PartnerResponse> create(@Valid @RequestBody PartnerCreateRequest req) {
        return ApiResponse.success(partnerService.create(req));
    }

    @Operation(summary = "거래처 수정", description = "코드는 불변. 없으면 404")
    @PutMapping("/{id}")
    public ApiResponse<PartnerResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody PartnerUpdateRequest req) {
        return ApiResponse.success(partnerService.update(id, req));
    }
}
