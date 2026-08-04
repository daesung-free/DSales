package com.daesung.sales.product.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Pattern;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 분류코드 형식 검증.
 *
 * <p><b>형식: [영문 1자][연도 4자][영문·숫자 1~3자]</b> — 총 6~8자. 예: {@code A2026A}, {@code M2026A01}
 * <ul>
 *   <li>첫 글자 = 대분류(A·H·M·N·S 등). 새 분류가 생길 수 있어 특정 글자로 못 박지 않는다.</li>
 *   <li>2~5번째 = 연도. 레거시는 분류코드에 연도를 박아 <b>해마다 새 코드를 만든다</b>.</li>
 *   <li>뒤 1~3자 = 일련. 법인마다 자릿수가 다르다(교재 1자, 연구소 3자).</li>
 * </ul>
 *
 * <p>근거: 레거시 실데이터 검증 — DSLab·DSTxtBook의 bookData·salesData 분류코드
 * <b>4,600건이 이 규칙에 100% 일치</b>(2017·2018·2025·2026년도 포함).
 * 자릿수를 8자로 고정하면 교재 법인(6자) 897건이 전부 막히므로 1~3자로 둔다.
 *
 * <p>왜 검증하는가: 분류코드는 매출 리포트 5종의 공유 축이다. 하나만 오타가 나도 그 건이
 * 집계에서 빠지거나 없는 분류로 새 행이 생겨 리포트가 조용히 어긋난다.
 * 지금까지는 자유 입력이라 걸러지지 않았다.
 */
@Documented
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Pattern(regexp = "^[A-Z]\\d{4}[A-Z0-9]{1,3}$",
        message = "분류코드 형식이 올바르지 않습니다. [영문1자][연도4자][영문·숫자1~3자] 예: A2026A, M2026A01")
public @interface CatCode {

    String message() default "분류코드 형식이 올바르지 않습니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
