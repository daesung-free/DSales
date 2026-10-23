package com.daesung.sales.common.idempotency;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 같은 요청을 <b>한 번만</b> 처리한다(Idempotency-Key).
 *
 * <p>근거: 프론트 실측 2026-10-22 ③-① — 위탁 매출 확정 중 연결이 끊기거나 504 가 나면
 * 사용자는 다시 누른다. 초과정산 차단은 발주처 확정으로 걷어냈기 때문에 서버가 막아 주지 않아
 * <b>매출이 두 번 잡힌다</b>. 프론트가 "반영됐을 수도 있음" 안내로 덮고 있었지만,
 * 그건 덮은 것이지 막은 것이 아니다.
 *
 * <h3>왜 "먼저 적고 나중에 실행"인가</h3>
 * 실행하고 나서 키를 적으면, 똑같은 요청 둘이 <b>동시에</b> 들어왔을 때 둘 다 조회를 통과해
 * 둘 다 실행된다 — 막으려던 바로 그 일이 벌어진다. 그래서 <b>키를 먼저 심고</b>(PK 충돌로 두 번째가 걸린다)
 * 그 다음에 실행한다.
 *
 * <h3>왜 응답 본문을 저장하나</h3>
 * 타임아웃 난 클라이언트는 <b>첫 응답을 받지 못했다</b>. 재시도에 "이미 처리됨"만 돌려주면
 * 결과(전표번호·매출번호)를 영영 모른다. 같은 키로 다시 부르면 <b>처음과 똑같은 응답</b>이 나가야 한다.
 *
 * <h3>‼️왜 {@code @Transactional} 이 아니라 TransactionTemplate 인가</h3>
 * 기록은 본 작업과 <b>별개 트랜잭션</b>이어야 한다 — 같이 묶으면 본 작업이 롤백될 때 키까지 사라지거나
 * (멱등이 깨진다), 반대로 키만 남아 <b>영영 재시도할 수 없는 상태</b>가 된다.
 * 그런데 그 메서드들을 같은 클래스 안에서 부르면 <b>프록시를 타지 않아 애너테이션이 무시된다</b>
 * (자기호출). 조용히 같은 트랜잭션에 묶여 버리므로, 전파를 눈에 보이게 템플릿으로 건다.
 */
@Slf4j
@Service
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate newTx;

    public IdempotencyService(IdempotencyRecordRepository repository, ObjectMapper objectMapper,
                              PlatformTransactionManager txManager) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.newTx = new TransactionTemplate(txManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 키가 없으면 그냥 실행한다(멱등 보장 없음 — 기존 동작 그대로).
     * 키가 있으면 같은 키의 재호출에 첫 결과를 그대로 돌려준다.
     */
    public <T> T runOnce(String key, String scope, Class<T> type, String actor, Supplier<T> action) {
        if (key == null || key.isBlank()) {
            return action.get();
        }
        Optional<IdempotencyRecord> existing = claim(key, scope, actor);
        if (existing.isPresent()) {
            return replay(existing.get(), type);
        }
        T result;
        try {
            result = action.get();
        } catch (RuntimeException e) {
            // 실패한 요청의 키는 **지운다** — 안 지우면 같은 키로 영영 다시 시도할 수 없다.
            newTx.executeWithoutResult(s -> repository.deleteById(key));
            throw e;
        }
        finish(key, result);
        return result;
    }

    /** 키를 선점한다. 이미 있으면 그 기록을 돌려준다(= 재호출). */
    private Optional<IdempotencyRecord> claim(String key, String scope, String actor) {
        return newTx.execute(s -> {
            Optional<IdempotencyRecord> found = repository.findById(key);
            if (found.isPresent()) {
                return found;
            }
            try {
                repository.saveAndFlush(IdempotencyRecord.start(key, scope, actor));
                return Optional.empty();
            } catch (DataIntegrityViolationException race) {
                // 똑같은 키가 **동시에** 들어왔다. 먼저 심은 쪽이 실행하고 이쪽은 재호출로 처리한다.
                return repository.findById(key);
            }
        });
    }

    private <T> T replay(IdempotencyRecord record, Class<T> type) {
        if (!record.isDone() || record.getResponseJson() == null) {
            // 앞의 요청이 아직 돌고 있다(또는 응답을 못 담았다).
            // 지금 실행하면 두 번 처리된다 — 기다렸다 결과를 조회하게 한다.
            throw new BusinessException(ErrorCode.DUPLICATE_REQUEST,
                    "같은 요청이 처리 중입니다. 잠시 후 조회 화면에서 결과를 확인해 주세요.");
        }
        try {
            return objectMapper.readValue(record.getResponseJson(), type);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // 저장된 응답을 못 읽는다고 **다시 실행하면 안 된다** — 그게 이중처리다.
            // ‼️로그에 DB 문자열(scope 등)을 인자로 넣지 않는다 — CRLF 위조 통로가 된다.
            //   예외는 Throwable 로 넘긴다(스택이 남고 위조 대상이 아니다).
            log.error("멱등 응답 복원 실패", e);
            throw new BusinessException(ErrorCode.DUPLICATE_REQUEST,
                    "이미 처리된 요청입니다. 결과를 조회 화면에서 확인해 주세요.");
        }
    }

    private void finish(String key, Object result) {
        newTx.executeWithoutResult(s -> repository.findById(key).ifPresent(r -> {
            try {
                r.complete(objectMapper.writeValueAsString(result));
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                // 본문을 못 담아도 **키는 닫는다** — 재시도가 다시 실행되는 것보다 낫다.
                // (replay 가 responseJson null 을 보고 409 로 막는다.)
                log.error("멱등 응답 직렬화 실패", e);
                r.complete(null);
            }
            repository.save(r);
        }));
    }
}
