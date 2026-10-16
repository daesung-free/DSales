package com.daesung.sales.sale.service;

import com.daesung.sales.common.sequence.SequenceService;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 등록 묶음번호(E-yyyyMMdd-n) 채번 — <b>한 번의 등록</b>을 가리키는 번호다(현장 피드백 2026-10-14 B-4).
 *
 * <p>매출번호(I-)는 줄마다 따로 채번돼 "한 번에 올린 것"을 표현하지 못한다. 담당자는
 * <b>순번으로 줄을 찾는다</b> — 여러 줄이 한 번에 들어가는 경로일수록 이게 더 필요하다.
 *
 * <p>★<b>여기로 모은 이유</b>: 묶음번호를 {@link SaleService#createEntries}에만 붙여 놓고
 * 파일 업로드·위탁정산·반품입고·매출일괄등록은 빼먹었다(프론트 실측 2026-10-16 B-2).
 * 전부 한 번에 여러 줄이 들어가는 경로인데, 하필 그쪽이 비어 있었다.
 * 채번을 한 곳으로 모아 두면 새 등록 경로를 만들 때 빠뜨리기 어렵다.
 */
@Service
@RequiredArgsConstructor
public class EntryNumberService {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SequenceService sequenceService;

    /** 그 날짜의 새 묶음번호 하나. 한 번의 등록(요청)마다 <b>한 번만</b> 부른다. */
    public String next(LocalDate date) {
        return "E-" + date.format(YYYYMMDD) + "-" + sequenceService.next(SequenceService.SEQ_SALES_ENTRY);
    }
}
