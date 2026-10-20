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

    @Schema(description = """
            정렬 기준: `컬럼명,정렬방향`. 비우면 정렬 안 함.

            ★**두 축 이상도 된다** — `sort` 를 여러 번 보내면 보낸 순서대로 적용한다(2026-10-08).
            예) `sort=partnerCode,asc&sort=salesDate,desc` → 거래처 먼저, 같은 거래처 안에서 날짜순.
            화면의 '정렬 우선순위(거래처→거래일 / 거래일→거래처)'가 이것이다.
            한 축만 보내던 기존 호출은 그대로 동작한다.""",
            example = "code,asc")
    private java.util.List<String> sort;

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
        if (sort == null || sort.isEmpty()) {
            // ★정렬을 안 줘도 **순서를 고정한다**(아래 TIEBREAK 주석 참고).
            return PageRequest.of(p, s, Sort.by(Sort.Order.asc(TIEBREAK)));
        }
        // ★보낸 순서가 곧 우선순위다. 화면의 '거래처 → 거래일' 같은 2단 정렬이 이것으로 표현된다.
        //
        // ‼️**방향이 따로 떨어져 들어온다.** `sort=salesDate,desc` 한 개를 보내도 스프링이
        //   쉼표로 끊어 `["salesDate", "desc"]` 두 개로 넘긴다 — 그래서 'desc' 를 정렬 항목으로
        //   보고 400 이 났다(프론트 실측 2026-10-12 A-2). `sort=항목,방향` 은 표준 형식이라
        //   화면뿐 아니라 엑셀 다운로드·외부 호출도 같이 막혀 있었다.
        //   → 토큰이 방향 하나뿐이면 **앞 항목의 방향**으로 붙인다. 앞 항목이 없으면 버린다
        //     (방향만 보낸 요청은 정렬 의미가 없다 — 거기서 400 을 내면 원인이 더 가려진다).
        java.util.List<Sort.Order> orders = new java.util.ArrayList<>();
        for (String one : sort) {
            if (one == null || one.isBlank()) {
                continue;
            }
            String[] parts = one.split(",");
            String property = parts[0].trim();

            if (isDirection(property) && parts.length == 1) {
                if (!orders.isEmpty()) {
                    Sort.Order last = orders.remove(orders.size() - 1);
                    orders.add(new Sort.Order(directionOf(property), last.getProperty()));
                }
                continue;
            }

            Sort.Direction direction = (parts.length > 1) ? directionOf(parts[1].trim())
                    : Sort.Direction.ASC;

            if (!aliases.isEmpty()) {
                String mapped = aliases.get(property);
                if (mapped == null) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT,
                            "정렬할 수 없는 항목입니다: " + property
                                    + ". 가능한 항목: " + String.join(", ", new java.util.TreeSet<>(aliases.keySet())));
                }
                property = mapped;
            }
            orders.add(new Sort.Order(direction, property));
        }
        // ★★**마지막에 id 를 덧붙인다 — 페이지 경계에서 행이 겹치거나 빠지는 것을 막는다.**
        //
        // 거래일자처럼 **같은 값이 많은 축**으로만 정렬하면 동순위 행들의 순서가 조회마다
        // 달라질 수 있다(DB 가 보장하지 않는다). 그러면 1쪽 끝과 2쪽 처음에 같은 행이
        // 두 번 나오고 다른 행은 아예 안 나온다.
        //   실측(프론트 2026-10-20 C-1): 작업요청서 2026년 23쪽을 받으면 **매번 한 건이
        //   두 쪽에 겹치고 한 건이 빠졌다**(2회 재현). 합계는 맞는데 목록이 틀린다 —
        //   눈에 잘 안 띄고, 엑셀로 내리면 그대로 굳는다.
        //
        // id 는 유일하므로 맨 뒤에 한 번만 붙이면 순서가 완전히 결정된다.
        // ‼️이미 id 로 정렬 중이면 덧붙이지 않는다(중복 정렬은 무의미하고 쿼리만 길어진다).
        // ‼️별칭 검사를 거치지 않는다 — 사용자 입력이 아니라 **우리가 넣는 값**이다.
        //   (네이티브 페이징 쿼리는 `findPageIncludingDeleted` 하나뿐이고 `SELECT s.*` 라
        //    `id` 가 출력 컬럼으로 해석된다 — 모호하지 않다.)
        if (orders.stream().noneMatch(o -> TIEBREAK.equals(o.getProperty()))) {
            orders.add(Sort.Order.asc(TIEBREAK));
        }
        return PageRequest.of(p, s, Sort.by(orders));
    }

    /** 순서를 확정짓는 마지막 정렬 항목. 모든 엔티티가 가진 유일값이다. */
    private static final String TIEBREAK = "id";

    private static boolean isDirection(String token) {
        return "asc".equalsIgnoreCase(token) || "desc".equalsIgnoreCase(token);
    }

    private static Sort.Direction directionOf(String token) {
        return "desc".equalsIgnoreCase(token) ? Sort.Direction.DESC : Sort.Direction.ASC;
    }
}
