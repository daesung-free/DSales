package com.daesung.sales.partner.entity;

import com.daesung.sales.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 거래처. 담보/여신은 채권 도메인용(custData.assureAmt/Exp/Note). 근거: custData. */
@Entity
@Table(name = "partners")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Partner extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    /** 거래처명2 = 합쳐진 풀네임(예: '진주 이룸도서'). 기존 name 유지. DSRE CUST_FNM. */
    @Column(nullable = false, length = 100)
    private String name;

    /** 도시명(예: '진주'). DSRE CITY_NM. */
    @Column(name = "city_name", length = 50)
    private String cityName;

    /** 거래처명1 = 상호만(예: '이룸도서'). DSRE CUST_NM. */
    @Column(name = "name1", length = 100)
    private String name1;

    /** 지역(관할, 12p 조회 컬럼. 레거시 custData.zone1 / DSRE CITY_NM). */
    @Column(length = 50)
    private String region;

    /** 거래처구분(특약점/기타학원/B2B/대성/자사몰 등, 12p 조회 컬럼. 레거시 type1). PartnerType(NORMAL/CONSIGN)과 별개 축. */
    @Column(name = "client_category", length = 30)
    private String clientCategory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PartnerType type = PartnerType.NORMAL;

    /** 담보금액(여신한도). 채권 담보비율=잔액/assureAmount. */
    @Column(name = "assure_amount")
    private Long assureAmount;

    /** 담보 만기일. */
    @Column(name = "assure_expiry")
    private LocalDate assureExpiry;

    /** 담보 내용(비고). */
    @Column(name = "assure_note", length = 500)
    private String assureNote;

    // ── 세무(계산서 공급받는자) 정보 ──
    @Column(name = "biz_no", length = 20)
    private String bizNo;          // 사업자번호

    @Column(name = "boss_name", length = 50)
    private String bossName;       // 대표자

    @Column(length = 200)
    private String addr1;

    @Column(length = 200)
    private String addr2;

    @Column(name = "biz_status", length = 100)
    private String bizStatus;      // 업태

    @Column(name = "biz_item", length = 100)
    private String bizItem;        // 종목

    @Column(length = 100)
    private String email1;

    @Column(length = 100)
    private String email2;

    // ── 거래처관리(30p) 연락처·거래기간 — 레거시 custData 컬럼 대응 ──────────────

    /** 사업자주민번호(custData.bossID). */
    @Column(name = "boss_id", length = 20)
    private String bossId;

    @Column(length = 20)
    private String tel1;

    @Column(length = 20)
    private String tel2;

    @Column(name = "cell_phone", length = 20)
    private String cellPhone;

    @Column(length = 20)
    private String fax;

    @Column(length = 10)
    private String zip;

    /** 관할지역(custData.zone2). region(지역, zone1)과 별개 축이다. */
    @Column(length = 200)
    private String zone2;

    /** 등록일(거래 시작). */
    @Column(name = "start_date")
    private LocalDate startDate;

    /**
     * 만료일. NULL이면 거래중.
     * 레거시 조회는 만료 안 된 거래처만 기본 노출하고, '만료된 거래처 포함' 체크 시 전체를 보여준다.
     */
    @Column(name = "end_date")
    private LocalDate endDate;

    /** 거래 만료 여부. */
    public boolean isExpired() {
        return endDate != null;
    }

    public static Partner create(String code, String name, PartnerType type) {
        Partner p = new Partner();
        p.code = code;
        p.name = name;
        p.type = (type == null) ? PartnerType.NORMAL : type;
        return p;
    }

    /** 수정(코드는 불변). name=거래처명2(풀네임), cityName=도시명, name1=상호만. */
    public void update(String name, String cityName, String name1, String region,
                       String clientCategory, PartnerType type) {
        this.name = name;
        this.cityName = cityName;
        this.name1 = name1;
        this.region = region;
        this.clientCategory = clientCategory;
        this.type = (type == null) ? PartnerType.NORMAL : type;
    }

    /** 거래처명 분리·지역·구분 필드 설정 — 등록 시 사용. */
    public void applyNames(String cityName, String name1, String region, String clientCategory) {
        this.cityName = cityName;
        this.name1 = name1;
        this.region = region;
        this.clientCategory = clientCategory;
    }

    /** 담보(여신) 정보 설정. */
    public void updateCredit(Long assureAmount, LocalDate assureExpiry, String assureNote) {
        this.assureAmount = assureAmount;
        this.assureExpiry = assureExpiry;
        this.assureNote = assureNote;
    }

    /**
     * 연락처·거래기간 설정(30p 거래처관리).
     *
     * <p>★사업자주민번호는 <b>마스킹된 값이 오면 무시</b>한다. 조회 응답이 마스킹돼 나가므로
     * 화면이 그 값을 그대로 담아 저장하면 원본이 {@code 800101-1******} 로 파괴된다.
     * 실제로 바꾸려면 마스킹되지 않은 전체 값을 보내야 한다.
     */
    public void updateContact(String bossId, String tel1, String tel2, String cellPhone, String fax,
                              String zip, String zone2, LocalDate startDate, LocalDate endDate) {
        if (!com.daesung.sales.common.mask.Masks.isMasked(bossId)) {
            this.bossId = bossId;
        }
        this.tel1 = tel1;
        this.tel2 = tel2;
        this.cellPhone = cellPhone;
        this.fax = fax;
        this.zip = zip;
        this.zone2 = zone2;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /** 세무(계산서 공급받는자) 정보 설정. */
    public void updateTaxInfo(String bizNo, String bossName, String addr1, String addr2,
                              String bizStatus, String bizItem, String email1, String email2) {
        this.bizNo = bizNo;
        this.bossName = bossName;
        this.addr1 = addr1;
        this.addr2 = addr2;
        this.bizStatus = bizStatus;
        this.bizItem = bizItem;
        this.email1 = email1;
        this.email2 = email2;
    }

    /**
     * 변경이력용 스냅샷(필드명|한글라벨 → 값). 근거: 발주처 확정 3-1(라) 기초정보 변경이력.
     *
     * <p>★사업자주민번호는 <b>마스킹해서</b> 담는다 — 이력 테이블에 원본이 쌓이면
     * 응답을 마스킹한 의미가 없어진다(이력 조회로 그대로 새어나간다).
     */
    public java.util.Map<String, String> auditSnapshot() {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        m.put("name|거래처명", name);
        m.put("name1|신고명", name1);
        m.put("cityName|도시명", cityName);
        m.put("region|지역", region);
        m.put("zone2|관할지역", zone2);
        m.put("clientCategory|거래처구분", clientCategory);
        m.put("type|유형", (type == null) ? null : type.name());
        m.put("bizNo|사업자번호", bizNo);
        m.put("bossName|사업자명", bossName);
        m.put("bossId|사업자주민번호", com.daesung.sales.common.mask.Masks.residentNo(bossId));
        m.put("bizStatus|업종", bizStatus);
        m.put("bizItem|업태", bizItem);
        m.put("tel1|연락처1", tel1);
        m.put("tel2|연락처2", tel2);
        m.put("cellPhone|휴대폰", cellPhone);
        m.put("fax|팩스", fax);
        m.put("email1|이메일1", email1);
        m.put("email2|이메일2", email2);
        m.put("zip|우편번호", zip);
        m.put("addr1|기본주소", addr1);
        m.put("addr2|상세주소", addr2);
        m.put("startDate|등록일", str(startDate));
        m.put("endDate|만료일", str(endDate));
        m.put("assureAmount|담보금액", str(assureAmount));
        m.put("assureExpiry|담보만기", str(assureExpiry));
        m.put("assureNote|담보내용", assureNote);
        return m;
    }

    private static String str(Object v) {
        return (v == null) ? null : String.valueOf(v);
    }
}
