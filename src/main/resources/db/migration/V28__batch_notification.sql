-- 배치잡 인프라 + 알림 적재. 근거: 개발문서 2탭 19.0(배치잡) · 25.0(담보만기 1개월전 알림 배치, 25p 재무팀 부활).
-- 레거시엔 배치 자체가 없어(BE-63) 인프라부터 신규 설계한다.
--
-- 설계 의도:
--  · batch_job_run  = 실행 이력. 개발문서 완료조건이 "배치 정상완료 + 실패 재시도 로직 검증"이라
--                     성공/실패와 재시도 횟수를 남겨야 검수 때 증거로 제시할 수 있다.
--  · notification   = 알림 적재. 메일 발송(SMTP)은 인프라 확정 전이라, 배치는 알림을 '쌓기'만 하고
--                     발송 채널은 뒤에 붙인다. 25p 팝업 UI(FE A11)가 이 테이블을 읽는다.

CREATE TABLE batch_job_run (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_name     VARCHAR(50)  NOT NULL COMMENT '잡 이름(예: COLLATERAL_EXPIRY)',
    run_date     DATE         NOT NULL COMMENT '기준일자',
    status       VARCHAR(10)  NOT NULL COMMENT 'SUCCESS/FAILED',
    attempt      INT          NOT NULL DEFAULT 1 COMMENT '해당 실행 내 시도 회차(재시도 포함)',
    affected     INT          NOT NULL DEFAULT 0 COMMENT '처리 건수(생성된 알림 수 등)',
    message      VARCHAR(500) NULL COMMENT '실패 사유 등',
    started_at   DATETIME(6)  NOT NULL,
    finished_at  DATETIME(6)  NULL,
    created_at   DATETIME     NOT NULL,
    created_by   VARCHAR(50)  NULL,
    updated_at   DATETIME     NULL,
    updated_by   VARCHAR(50)  NULL
) COMMENT '배치 실행 이력';
-- 유니크를 걸지 않는다: 같은 날 여러 번 실행하는 것이 정상 동작이다(수동 재실행·재시도).
-- 멱등성은 notification.dedup_key가 담당한다 — 몇 번 돌려도 알림은 늘지 않는다.

CREATE INDEX ix_batch_job_run_name ON batch_job_run (job_name, run_date);

CREATE TABLE notification (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    type         VARCHAR(30)  NOT NULL COMMENT '알림유형(COLLATERAL_EXPIRY 등)',
    dedup_key    VARCHAR(150) NOT NULL COMMENT '중복 적재 방지 키 — 배치 재실행해도 같은 알림이 쌓이지 않게',
    title        VARCHAR(200) NOT NULL,
    message      VARCHAR(500) NULL,
    ref_type     VARCHAR(30)  NULL COMMENT '연관 대상 종류(PARTNER 등)',
    ref_id       BIGINT       NULL COMMENT '연관 대상 id',
    notify_date  DATE         NOT NULL COMMENT '알림 발생 기준일',
    read_yn      BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '확인 여부',
    created_at   DATETIME     NOT NULL,
    created_by   VARCHAR(50)  NULL,
    updated_at   DATETIME     NULL,
    updated_by   VARCHAR(50)  NULL,
    CONSTRAINT uq_notification_dedup UNIQUE (dedup_key)
) COMMENT '알림 적재(팝업·후속 메일 발송 공용)';

CREATE INDEX ix_notification_unread ON notification (read_yn, notify_date);
CREATE INDEX ix_notification_type ON notification (type, notify_date);
