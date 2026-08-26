package com.daesung.sales.dsre.gateway;

import java.time.LocalDate;

/**
 * 매출일괄등록(14p, 더프) 대상 한 행. 근거: 레거시 {@code 매출가져오기.vb:415} 읽기쿼리 +
 * 정본 14p 데이터 항목 {@code 처리순번, 신청일자, 매출코드, 거래처코드/명, 학교코드/명, 학년,
 * 처리구분, 청구구분, 신청/처리/비처리 인원, 단가, 총금액}.
 *
 * <p>★더프는 <b>응시인원 기반 종량제</b>다 — 교재({@link BooklistImportRow})의 부수 기반과 계산이 다르다.
 * 그래서 별도 화면·별도 경로다(정본 14p "타 상품군의 SET수량 기반과 계산방식 상이").
 *
 * <p>★<b>단가·청구인원·총금액은 여기 없다</b>. 레거시는 그 셋을 SQL CASE 안에서 계산하는데,
 * 그러면 화면 콤보값(처리구분)이 SQL 문자열에 끼어들어 조건마다 다른 쿼리가 나간다.
 * 이 record는 <b>인원 4종을 다 실어 오고</b>, 어느 인원으로 청구할지는 Java가 고른다 —
 * 미리보기와 실제 등록이 같은 판정을 쓰도록 한 곳에 모으기 위해서다.
 *
 * @param reqCd        처리순번(신청번호)
 * @param reqDate      신청일자
 * @param machulCode   매출코드(거래처의 매출용 코드 — 우리 거래처코드와 맞춘다)
 * @param custCode     DSRE 거래처코드
 * @param custName     거래처명(상호)
 * @param custFullName 거래처 풀네임
 * @param cityName     도시/관할명
 * @param schoolCode   학교/학원 코드(MGR_CD)
 * @param schoolName   학교/학원명
 * @param grade        학년
 * @param prodCode     분류코드(상품)
 * @param prodName     분류명
 * @param dtlCode      과목코드(상세)
 * @param dtlName      과목명
 * @param subjectCnt   신청과목수
 * @param procType     처리구분(proc_yn2). <b>Y/N이 아니다</b> — 복제본 실측값은
 *                     {@code '1'·'5'·'N'}(숫자는 회차, 레거시 「회차」 콤보가 이 값으로 거른다).
 *                     판정은 레거시 그대로 <b>'N'이냐 아니냐</b>만 본다: N이면 비처리, 그 외는 처리
 * @param chargeType   청구구분. T=처리+비처리 / S=처리 / R=등록
 * @param reqInwon     신청인원(FUNC_REQINWON_GET — 최대인원)
 * @param procInwon    처리인원
 * @param unprocInwon  비처리인원(신청 + 조정 − 처리)
 * @param regInwon     등록인원
 * @param price        정가
 * @param supplyRate   공급률(%)
 * @param discount     할인액(권당). 0보다 크면 공급률 대신 이 값으로 단가가 정해진다
 * @param memo         비고
 */
public record DuffSalesRow(
        int reqCd,
        LocalDate reqDate,
        String machulCode,
        String custCode,
        String custName,
        String custFullName,
        String cityName,
        String schoolCode,
        String schoolName,
        String grade,
        String prodCode,
        String prodName,
        String dtlCode,
        String dtlName,
        int subjectCnt,
        String procType,
        String chargeType,
        int reqInwon,
        int procInwon,
        int unprocInwon,
        int regInwon,
        int price,
        int supplyRate,
        int discount,
        String memo
) {
}
