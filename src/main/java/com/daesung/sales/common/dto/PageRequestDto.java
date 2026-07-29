package com.daesung.sales.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 목록 조회 공통 페이징 요청. springdoc이 이 필드의 @Schema(한글)를 그대로 노출한다.
 * (스프링 기본 Pageable은 설명이 영어로 하드코딩돼 바꿀 수 없어 커스텀 사용)
 */
@Getter
@Setter
public class PageRequestDto {

    @Schema(description = "페이지 번호 (0부터 시작)", example = "0", defaultValue = "0")
    private Integer page = 0;

    @Schema(description = "페이지 크기 (기본 20)", example = "20", defaultValue = "20")
    private Integer size = 20;

    @Schema(description = "정렬 기준: 컬럼명,정렬방향 (예: code,asc / price,desc). 비우면 정렬 안 함",
            example = "code,asc")
    private String sort;

    /** 페이지 크기 상한(자원 고갈 방지). */
    private static final int MAX_SIZE = 200;

    public Pageable toPageable() {
        int p = (page == null || page < 0) ? 0 : page;
        int s = (size == null || size < 1) ? 20 : Math.min(size, MAX_SIZE);
        if (sort == null || sort.isBlank()) {
            return PageRequest.of(p, s);
        }
        String[] parts = sort.split(",");
        String property = parts[0].trim();
        Sort.Direction direction = (parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim()))
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return PageRequest.of(p, s, Sort.by(direction, property));
    }
}
