package com.daesung.sales.product.entity;

/**
 * BOM 자재구분(33p 도서관리 세트구성 상세 탭).
 *
 * <p>정본 33p·36p 기준으로 <b>물류비용등록의 '작업구분'과 1:1 대응</b>되는 축이다.
 * 9p 세트 조립 시 발생하는 물류작업비(포장비)를 어느 단가에 매길지 정하는 연결고리라,
 * 이 구분이 없으면 작업비 연동 자체가 성립하지 않는다.
 */
public enum MaterialType {
    /** 시험지 */
    EXAM_PAPER,
    /** 해설지 */
    ANSWER_SHEET,
    /** OMR 카드 */
    OMR,
    /** 라벨 */
    LABEL,
    /** 기타 자재 */
    ETC
}
