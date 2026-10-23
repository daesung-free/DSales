package com.daesung.sales.common.tx;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 우리 트랜잭션이 <b>롤백되면</b> 실행할 보상 작업을 건다.
 *
 * <p>DSRE2 는 별도 DB 라 우리 트랜잭션에 묶이지 않는다. DSRE 쪽을 먼저 커밋한 뒤 우리 저장이
 * 실패하면 두 장부가 어긋난다 — 그때 DSRE 를 원래대로 되돌리는 데 쓴다.
 *
 * <p>보상이 실패해도 예외를 던지지 않는다(이미 롤백 중이라 받을 곳이 없다). 대신 오류 로그를 남겨
 * 사람이 손으로 맞출 수 있게 한다.
 */
@Slf4j
public final class AfterRollback {

    private AfterRollback() {
    }

    public static void register(String what, Runnable compensation) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;   // 트랜잭션 밖이면 롤백도 없다
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_ROLLED_BACK) {
                    return;
                }
                try {
                    compensation.run();
                    log.warn("우리 저장이 롤백되어 DSRE 를 되돌렸습니다: {}", what);
                } catch (RuntimeException e) {
                    log.error("‼️DSRE 되돌리기 실패 — 수동 확인 필요: {}", what, e);
                }
            }
        });
    }
}
