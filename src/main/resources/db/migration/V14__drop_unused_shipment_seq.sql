-- 미사용 채번 제거. seq_shipment_no(S 출고번호)는 어디서도 채번 호출 없음(감사에서 발견).
-- 정상출고는 별도 출고전표 없이 매출번호(I)로 흡수 → S 채번 불필요.
-- MySQL 전환 후 seq_registry 기반이므로 V1에서 애초에 seq_shipment_no를 넣지 않음(별도 삭제 불요).
-- (이 파일은 이력 연속성 유지를 위한 no-op)
SELECT 1;
