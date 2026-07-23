-- 미사용 시퀀스 제거. seq_shipment_no(S 출고번호)는 V1에서 선언됐으나 어디서도 nextval 호출 없음.
-- 정상출고는 별도 출고전표 없이 매출번호(I, seq_invoice_no)로 흡수 → S 채번 불필요. (감사에서 발견)
DROP SEQUENCE IF EXISTS seq_shipment_no;
