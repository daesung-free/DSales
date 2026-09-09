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

    /**
     * 이미 <b>메모리에 다 들어온 목록</b>을 페이지 형태로 감싼다.
     *
     * <p>★진짜 페이징이 아니다. 원본이 페이징을 지원하지 않을 때만 쓴다 —
     * 예: DSRE2 주문조회는 저장함수로 인원을 계산하며 한 번에 결과를 만든다.
     * 그런 곳에서 억지로 LIMIT을 걸면 합계·인원이 페이지마다 달라진다.
     * 전량을 받아 여기서 잘라 내되, 총건수는 <b>자른 뒤가 아니라 전체</b>를 준다.
     */
    public static <T> PageResponse<T> ofList(List<T> all, int page, int size) {
        int safeSize = (size <= 0) ? 20 : size;
        int safePage = Math.max(page, 0);
        int from = Math.min(safePage * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        int totalPages = (int) Math.ceil((double) all.size() / safeSize);
        return new PageResponse<>(all.subList(from, to), safePage, safeSize,
                all.size(), totalPages, to >= all.size());
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
