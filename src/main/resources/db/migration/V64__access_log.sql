-- 사용자 행위 기록(접근로그). 근거: 발주처 요청(2026-09-11) —
--   "아이디별로 사용기록이 남게, 로그기록을 볼 수 있는 페이지가 필요. 보안상 중요.
--    직원의 다운로드기록같은걸 볼 수 있게".
--
-- ★기존 감사 테이블과 다른 것을 기록한다
--     master_change_log  무엇이 어떻게 바뀌었나 (값의 전/후)
--     status_history     상태가 어떻게 옮겨졌나
--     access_log         **누가 언제 무엇을 했나** — 특히 파일을 받아 갔나
--   앞의 둘은 '쓰기'만 남는다. 거래처 사업자번호·전 매출이 담긴 엑셀이 나가도 흔적이 없었다.
--
-- ★조회(GET 목록)는 기록하지 않는다
--   목록 한 번 열 때마다 한 줄이 쌓이면 하루 수만 건이 되고, 정작 봐야 할 **다운로드 기록이
--   그 안에 묻힌다.** 다운로드·로그인·쓰기만 남긴다(발주처 선택 B).

CREATE TABLE access_log
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    username     VARCHAR(50)  NOT NULL COMMENT '행위자 로그인 ID. 비로그인(로그인 시도)은 시도한 ID',
    role         VARCHAR(20)  NULL COMMENT '행위 시점 역할 — 나중에 역할이 바뀌어도 당시 권한을 알 수 있다',
    action       VARCHAR(20)  NOT NULL COMMENT 'DOWNLOAD / LOGIN / LOGOUT / CREATE / UPDATE / DELETE',
    menu         VARCHAR(60)  NULL COMMENT '화면·기능 이름(경로에서 유추). 담당자가 읽을 값',
    method       VARCHAR(10)  NOT NULL COMMENT 'HTTP 메서드',
    path         VARCHAR(300) NOT NULL COMMENT '요청 경로(쿼리 제외)',
    query        VARCHAR(500) NULL COMMENT '조회조건. ‼️토큰·비밀번호는 담지 않는다',
    status       INT          NOT NULL COMMENT 'HTTP 상태. 실패도 남긴다 — 막힌 시도가 더 중요할 때가 있다',
    success      BOOLEAN      NOT NULL COMMENT '2xx 여부',
    file_name    VARCHAR(200) NULL COMMENT '다운로드 파일명',
    file_size    BIGINT       NULL COMMENT '다운로드 바이트 수 — 얼마나 가져갔는지의 단서',
    client_ip    VARCHAR(45)  NULL COMMENT '요청 IP(IPv6 대비 45자)',
    user_agent   VARCHAR(300) NULL COMMENT '브라우저 정보',
    took_ms      INT          NULL COMMENT '처리 시간(ms)',
    created_at   DATETIME(6)  NOT NULL COMMENT '발생 시각'
) COMMENT '사용자 행위 기록 — 추가만 하고 수정·삭제하지 않는다(보존기간 경과분 일괄 삭제 제외)';

-- "이 사람이 무엇을 했나" — 가장 잦은 질의
CREATE INDEX ix_access_log_user ON access_log (username, created_at);
-- "이 기간에 무엇이 나갔나"
CREATE INDEX ix_access_log_action ON access_log (action, created_at);
-- 보존기간 정리·기간 조회
CREATE INDEX ix_access_log_at ON access_log (created_at);
