package com.daesung.sales.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 도서 등록 화면의 <b>분류코드 선택 목록</b> 한 줄.
 *
 * <p>★<b>분류 마스터 테이블이 아니다.</b> 정본(1탭 32p)은 분류코드·분류명을
 * 도서 기본정보의 <b>입력 항목</b>으로 둔다 — 분류를 따로 등록·관리하는 화면이 없다.
 * 레거시도 {@code bookData}에 코드·명을 직접 넣는 구조였다.
 *
 * <p>그래서 이 목록은 <b>이미 쓰이고 있는 값을 모아 보여주는 것</b>이다.
 * 담당자가 {@code K202601}을 기억해 타이핑하는 대신 고르게 하려는 것이고,
 * 새 분류는 여전히 직접 입력할 수 있다.
 *
 * <p>왜 필요한가: 형식 검증(@CatCode)은 <b>오타를 못 잡는다</b>.
 * {@code K202601}을 {@code K202602}로 잘못 치면 형식은 통과하고
 * 리포트에 없던 분류가 한 줄 더 생겨 매출이 조용히 쪼개진다.
 */
@Schema(description = "분류코드 선택 목록 — 이미 쓰이는 값 모음(마스터 테이블 아님)")
public record ProductCategoryRow(

        @Schema(description = "분류코드", example = "K202601") String catCode,

        @Schema(description = "분류명", example = "국어모의") String catName,

        @Schema(description = """
                이 분류를 쓰는 도서 수.
                ★같은 코드에 분류명이 갈려 있으면 **여러 줄로 나온다** — 숨기지 않는다.
                건수가 1인 줄이 옆에 비슷한 이름으로 있으면 오타를 의심할 자리다.""",
                example = "12")
        long productCount
) {
}
