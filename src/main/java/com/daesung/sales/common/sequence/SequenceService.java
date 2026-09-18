package com.daesung.sales.common.sequence;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채번(전표번호 순번) 서비스. MySQL 8 시퀀스 부재 → seq_registry 행잠금으로 원자 채번.
 * 상수: SEQ_INVOICE(I 매출)/SEQ_PURGE(P 폐기)/SEQ_CONSIGNMENT(OUT 위탁)/SEQ_COLLECTION(수금)/SEQ_STOCKTAKE(실사).
 * 호출 트랜잭션에 참여(REQUIRED) — UPDATE 행잠금이 커밋까지 유지되어 동시 채번 직렬화.
 */
@Service
@RequiredArgsConstructor
public class SequenceService {

    public static final String SEQ_INVOICE = "seq_invoice_no";
    public static final String SEQ_PURGE = "seq_purge_no";
    public static final String SEQ_CONSIGNMENT = "seq_consignment_no";
    public static final String SEQ_COLLECTION = "seq_collection_no";
    public static final String SEQ_STOCKTAKE = "seq_stocktake_no";

    /** IN: 입고번호. ‼️예전엔 입고에 전표번호가 없어 "무엇을 되돌릴지" 특정할 수 없었다(취소 신설 시 추가). */
    public static final String SEQ_INBOUND = "seq_inbound_no";

    /** 이고 전표(TR-). 없으면 이고를 되돌릴 수 없다 — 취소·삭제가 전표번호로 대상을 찾는다. */
    public static final String SEQ_TRANSFER = "seq_transfer_no";

    /** 세트 조립·해체 전표(BW-). 이고와 같은 이유. */
    public static final String SEQ_BOMWORK = "seq_bomwork_no";

    private final SequenceRepository repository;

    /** 다음 순번(원자적). */
    @Transactional(propagation = Propagation.REQUIRED)
    public long next(String seqName) {
        int updated = repository.bump(seqName);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "채번 시퀀스 없음: " + seqName);
        }
        Long val = repository.current(seqName);
        return (val == null) ? 0L : val;
    }
}
