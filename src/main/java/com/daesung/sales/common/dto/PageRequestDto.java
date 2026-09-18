package com.daesung.sales.common.dto;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
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
        return toPageable(java.util.Map.of());
    }

    /**
     * 정렬 <b>별칭표</b>를 받아 Pageable 로 변환한다.
     *
     * <p>★화면 컬럼명과 엔티티 속성명은 같지 않다. 매출 목록의 '학년'은 화면에선 {@code grade}인데
     * {@code Sale}엔 그 속성이 없고 {@code product.grade}에 있다. 별칭을 안 두면 화면이
     * 보이는 이름으로 정렬을 걸었을 때 <b>500</b>이 난다(Hibernate 가 속성을 못 찾는다).
     *
     * <p>‼️<b>모르는 정렬 항목은 400으로 거부한다.</b> 예전엔 받은 문자열을 그대로
     * JPQL 뒤에 붙여, 오타 하나가 서버 오류(500)로 나갔다. 무엇으로 정렬할 수 있는지는
     * 화면이 알 수 없으므로 메시지에 목록을 함께 준다.
     *
     * @param aliases 허용 정렬 항목 → 엔티티 속성 경로. 비어 있으면 검사하지 않는다(기존 호출부 호환)
     */
    public Pageable toPageable(java.util.Map<String, String> aliases) {
        int p = (page == null || page < 0) ? 0 : page;
        int s = (size == null || size < 1) ? 20 : Math.min(size, MAX_SIZE);
        if (sort == null || sort.isBlank()) {
            return PageRequest.of(p, s);
        }
        String[] parts = sort.split(",");
        String property = parts[0].trim();
        Sort.Direction direction = (parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim()))
                ? Sort.Direction.DESC : Sort.Direction.ASC;

        if (!aliases.isEmpty()) {
            String mapped = aliases.get(property);
            if (mapped == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "정렬할 수 없는 항목입니다: " + property
                                + ". 가능한 항목: " + String.join(", ", new java.util.TreeSet<>(aliases.keySet())));
            }
            property = mapped;
        }
        return PageRequest.of(p, s, Sort.by(direction, property));
    }
}
