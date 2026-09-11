-- 사용자 개별 권한 키 정규화. 근거: 개발팀 점검(2026-09-11)
--   "마감 권한 부여가 저장되지 않습니다 — 200을 주면서 저장하지 않고, 화면은 성공했다고 알립니다."
--
-- ★원인: 화면은 `periodLock`, 서버는 `PERIOD_LOCK`을 읽었다.
--   저장 쪽이 문자열을 검증 없이 받아 **아무도 읽지 않는 행**을 만들었다.
--   조회 응답 필드명이 periodLock이라 그 이름으로 되보내는 건 자연스러운 일이었다 —
--   막았어야 할 쪽은 서버다(이제 UserPermissionFlag.normalizeKey 가 정규화하고 모르는 키는 거부).
--
-- 여기서는 이미 쌓인 행을 대문자로 올린다. 그냥 두면 사용자는 "체크했는데 왜 안 되지"를
-- 반복하고, 나중에 정규화된 행이 따로 생겨 **같은 사용자·같은 권한이 두 줄**이 된다.

-- '_' 유무·대소문자 관계없이 모아서 표준 키로 바꾼다.
UPDATE user_permission_flag
SET flag_key = 'PERIOD_LOCK'
WHERE UPPER(REPLACE(flag_key, '_', '')) = 'PERIODLOCK';

UPDATE user_permission_flag
SET flag_key = 'PERIOD_UNLOCK'
WHERE UPPER(REPLACE(flag_key, '_', '')) = 'PERIODUNLOCK';

-- ‼️정규화로 (user_id, flag_key)가 겹칠 수 있다 — 같은 권한이 낙타/대문자로 두 줄이던 경우.
--   나중 것(id가 큰 쪽)이 담당자의 마지막 의사이므로 그걸 남기고 앞의 것을 지운다.
DELETE f FROM user_permission_flag f
JOIN user_permission_flag later
  ON later.user_id = f.user_id
 AND later.flag_key = f.flag_key
 AND later.id > f.id;
