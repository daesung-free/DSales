package com.daesung.sales.partner.service;

import com.daesung.sales.audit.entity.MasterEntityType;
import com.daesung.sales.audit.service.MasterChangeLogService;
import com.daesung.sales.common.code.MasterCodes;
import com.daesung.sales.common.excel.ExcelSheetReader;
import com.daesung.sales.common.excel.ExcelSheetReader.Header;
import com.daesung.sales.common.excel.ExcelSheetReader.RowView;
import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.partner.entity.Partner;
import com.daesung.sales.partner.entity.PartnerType;
import com.daesung.sales.partner.repository.PartnerRepository;
import com.daesung.sales.product.dto.MasterUploadResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 거래처 <b>엑셀 업로드</b>(30p 거래처관리).
 *
 * <p>★<b>왜 필요한가.</b> 거래처는 매출·재고·채권이 전부 물려 있는 FK 타깃이라
 * 실사용 개시 전에 수백 건이 한 번에 들어가야 한다. 그런데 등록 경로가 단건 화면뿐이었다.
 * 도서는 {@code POST /masters/products/upload}가 이미 있는데 거래처만 없었다.
 *
 * <p>★<b>양식은 거래처 목록 다운로드 파일 그대로다.</b> 헤더 이름으로 읽으므로
 * ({@link ExcelSheetReader}) 열 순서가 달라도, 모르는 열이 붙어 있어도 동작한다.
 * "받은 파일을 고쳐서 다시 올린다"가 이 기능의 주 사용법이다.
 *
 * <p>★<b>등록 화면보다 검증이 느슨하다 — 의도한 것이다.</b>
 * {@code PartnerCreateRequest}는 사업자번호에 형식 검증을 걸지만, 여기서는 걸지 않는다.
 * 초기 적재 원본에는 마스킹된 값·빈 값·옛 표기가 섞여 있고, 그런 행을 거부하면
 * 담당자는 수백 줄을 손으로 고친 뒤에야 한 건이라도 올릴 수 있다.
 * 적재가 먼저고 정제는 그 다음이다 — 대신 무엇이 들어갔는지 행 단위로 돌려준다.
 *
 * <p>★<b>한 행이 틀려도 나머지는 들어간다.</b> 행별 결과·사유를 주고 성공분은 남긴다.
 * 다만 <b>거래처코드 열이 없는 파일</b>은 양식 자체가 틀린 것이므로 파일 전체를 거부한다.
 *
 * <p>‼️<b>빈 칸은 "지우기"가 아니라 "그대로"</b>다. 담당자는 고칠 열만 채워 올린다 —
 * 빈 칸을 null로 덮으면 연락처만 고치려던 사람이 담보·주소를 통째로 날린다.
 * (거래처 수정 API가 전체 교체라 실제로 사고가 났던 지점이다.)
 */
@Service
@RequiredArgsConstructor
public class PartnerUploadService {

    private final PartnerRepository partnerRepository;
    private final MasterChangeLogService masterChangeLogService;

    private static final Header H_CODE = Header.of("거래처코드", "code", "custCode");
    private static final Header H_NAME = Header.of("거래처명", "거래처명2", "custName");
    private static final Header H_NAME1 = Header.of("신고명", "거래처명1", "상호", "custName2");
    private static final Header H_BIZ_NO = Header.of("사업자번호", "custNum");
    private static final Header H_BIZ_STATUS = Header.of("업종", "tradeStatus");
    private static final Header H_BIZ_ITEM = Header.of("업태", "종목", "tradeType");
    private static final Header H_BOSS_NAME = Header.of("사업자명", "대표자", "bossName");
    private static final Header H_BOSS_ID = Header.of("사업자주민번호", "bossID");
    private static final Header H_TEL1 = Header.of("연락처1", "tel1");
    private static final Header H_TEL2 = Header.of("연락처2", "tel2");
    private static final Header H_CELL = Header.of("휴대폰번호", "휴대폰", "cellPhone");
    private static final Header H_FAX = Header.of("팩스번호", "팩스", "fax");
    private static final Header H_EMAIL1 = Header.of("이메일1", "eMail1");
    private static final Header H_EMAIL2 = Header.of("이메일2", "eMail2");
    private static final Header H_ZIP = Header.of("우편번호", "zip");
    private static final Header H_ADDR1 = Header.of("기본주소", "주소", "addr1");
    private static final Header H_ADDR2 = Header.of("상세주소", "addr2");
    private static final Header H_START = Header.of("등록일", "거래시작일", "startDate");
    private static final Header H_END = Header.of("만료일", "거래종료일", "endDate");
    private static final Header H_CITY = Header.of("도시명", "cityName");
    private static final Header H_REGION = Header.of("지역", "zone1");
    private static final Header H_ZONE2 = Header.of("관할지역", "zone2");
    private static final Header H_CATEGORY = Header.of("거래처구분", "custType");
    private static final Header H_ASSURE_AMT = Header.of("담보금액", "assureAmt");
    private static final Header H_ASSURE_EXP = Header.of("담보만기", "담보만기일", "assureExp");
    private static final Header H_ASSURE_NOTE = Header.of("담보내용", "assureNote");

    /** 거래처코드가 키다 — 있으면 고치고 없으면 만든다. */
    @Transactional
    public MasterUploadResponse upload(MultipartFile file) {
        ExcelSheetReader sheet = ExcelSheetReader.read(file, List.of(H_CODE));
        List<MasterUploadResponse.Line> lines = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int failed = 0;

        for (RowView r : sheet.rows()) {
            String code = null;
            try {
                code = r.required(H_CODE);
                Partner existing = partnerRepository.findByCode(code).orElse(null);

                if (existing == null) {
                    // 신규는 거래처명이 있어야 한다 — 이름 없는 거래처는 화면에서 고를 수가 없다.
                    Partner p = Partner.create(code, r.required(H_NAME), PartnerType.NORMAL);
                    apply(p, r, null);
                    partnerRepository.save(p);
                    lines.add(new MasterUploadResponse.Line(r.rowNo(), "CREATED", code, null));
                    created++;
                } else {
                    Map<String, String> before = existing.auditSnapshot();
                    apply(existing, r, existing);
                    masterChangeLogService.recordDiff(MasterEntityType.PARTNER, existing.getId(),
                            existing.getCode(), before, existing.auditSnapshot());
                    lines.add(new MasterUploadResponse.Line(r.rowNo(), "UPDATED", code, null));
                    updated++;
                }
            } catch (BusinessException e) {
                lines.add(new MasterUploadResponse.Line(r.rowNo(), "ERROR", code, e.getMessage()));
                failed++;
            }
        }
        lines.sort(java.util.Comparator.comparingInt(MasterUploadResponse.Line::row));
        return new MasterUploadResponse(created, updated, failed, lines);
    }

    /**
     * 엑셀 한 행을 엔티티에 반영한다. {@code current}가 있으면(수정) 빈 칸은 기존 값을 유지한다.
     *
     * <p>엔티티의 {@code update*} 메서드가 전부 <b>전체 교체</b>라, 유지할 값은
     * 여기서 채워 넘긴다. 엔티티 쪽을 "null=유지"로 바꾸지 않는 이유는
     * 거래처 수정 API(화면)가 그 전체 교체 의미에 의존하고 있어서다.
     */
    private void apply(Partner p, RowView r, Partner current) {
        p.update(
                or(r.str(H_NAME), current == null ? null : current.getName()),
                or(r.str(H_CITY), current == null ? null : current.getCityName()),
                or(r.str(H_NAME1), current == null ? null : current.getName1()),
                or(r.str(H_REGION), current == null ? null : current.getRegion()),
                or(MasterCodes.clientCategory(r.str(H_CATEGORY)),
                        current == null ? null : current.getClientCategory()),
                current == null ? PartnerType.NORMAL : current.getType());

        p.updateTaxInfo(
                or(r.str(H_BIZ_NO), current == null ? null : current.getBizNo()),
                or(r.str(H_BOSS_NAME), current == null ? null : current.getBossName()),
                or(r.str(H_ADDR1), current == null ? null : current.getAddr1()),
                or(r.str(H_ADDR2), current == null ? null : current.getAddr2()),
                or(r.str(H_BIZ_STATUS), current == null ? null : current.getBizStatus()),
                or(r.str(H_BIZ_ITEM), current == null ? null : current.getBizItem()),
                or(r.str(H_EMAIL1), current == null ? null : current.getEmail1()),
                or(r.str(H_EMAIL2), current == null ? null : current.getEmail2()));

        // ★주민번호는 엔티티가 마스킹된 값을 걸러낸다 — 목록 다운로드가 마스킹돼 나가므로
        //   그 파일을 그대로 다시 올려도 원본이 파괴되지 않는다.
        p.updateContact(
                or(r.str(H_BOSS_ID), current == null ? null : current.getBossId()),
                or(r.str(H_TEL1), current == null ? null : current.getTel1()),
                or(r.str(H_TEL2), current == null ? null : current.getTel2()),
                or(r.str(H_CELL), current == null ? null : current.getCellPhone()),
                or(r.str(H_FAX), current == null ? null : current.getFax()),
                or(r.str(H_ZIP), current == null ? null : current.getZip()),
                or(r.str(H_ZONE2), current == null ? null : current.getZone2()),
                or(r.dateOrNull(H_START), current == null ? null : current.getStartDate()),
                or(r.dateOrNull(H_END), current == null ? null : current.getEndDate()));

        p.updateCredit(
                or(r.longOrNull(H_ASSURE_AMT), current == null ? null : current.getAssureAmount()),
                or(r.dateOrNull(H_ASSURE_EXP), current == null ? null : current.getAssureExpiry()),
                or(r.str(H_ASSURE_NOTE), current == null ? null : current.getAssureNote()));
    }

    private static <T> T or(T incoming, T fallback) {
        return (incoming != null) ? incoming : fallback;
    }
}
