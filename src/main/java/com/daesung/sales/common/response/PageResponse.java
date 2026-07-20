package com.daesung.sales.common.response;

import java.util.List;
import lombok.Getter;
import org.springframework.data.domain.Page;

/** 목록 API 공통 페이징 응답. Spring Data {@link Page}를 표준 형태로 변환한다. */
@Getter
public class PageResponse<T> {

    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    private final boolean last;

    private PageResponse(List<T> content, int page, int size,
                         long totalElements, int totalPages, boolean last) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.last = last;
    }

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }
}
