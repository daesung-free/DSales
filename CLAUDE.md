# CLAUDE.md — 대성 매출프로그램 재구축 프로젝트

> 이 파일은 세션마다 자동 로드되는 프로젝트 컨텍스트다. 작업 시작 전 반드시 여기 담긴 **대전제·핵심로직·Phase0 블로커·주의점**을 기준으로 판단할 것.
> 정본(source of truth)은 접근통제된 **구글시트 2종**(§8). 이 파일은 그 요약이며, 세부·최신은 시트를 다시 읽어 확인한다.

---

## 1. 프로젝트 한 줄
대성 학력개발의 **매출프로그램(백오피스)** 을 레거시(VB.NET WinForms 데스크톱)에서 **Java/React 웹**으로 재구축 + 특약점 사이트(order.dsdo.co.kr) 개편.
도메인 본질: **위탁(선출고 후정산) 비즈니스를 지탱하는 재고·매출 상태머신** — 물류(재고) 장부와 재무(매출) 장부를 **이중으로** 추적한다.

- 일정: 개발 완료 목표 **2026-10-31** / 실사용 개시 **2026-11-말**
- 발주: 대성학력개발연구소(사업부 전략팀), 회신 1차 박희원 과장

## 2. 현재 코드베이스 상태 (실측)
- **스택 확정(문서 기준)**: Spring Boot **3.4.5** · Java **21** · Gradle **8.14** · JPA + **QueryDSL 5.1.0(jakarta)** · **PostgreSQL**. 패키지 `com.daesung.sales`, 진입점 `SalesApplication.java`. 컴파일 검증 완료.
  - Gradle 래퍼는 8.14(Boot 3.4.x 공식 지원 라인). Java 21은 `foojay-resolver`(settings.gradle)로 자동 프로비저닝.
  - build.gradle 의존성: web·validation·actuator / data-jpa·postgresql·querydsl / flyway(스키마 마이그레이션) / security / poi-ooxml(엑셀) / springdoc(OpenAPI) / lombok / test(testcontainers-postgresql, security-test).
  - 📌 결정 로그: **Flyway는 시트에 명시되지 않은 Claude 추가 권장 → 사용자 승인(유지).** 신규 스키마 대량이라 DDL 버전관리 목적. 변경 파일은 `src/main/resources/db/migration/V*.sql`.
  - 📌 결정 로그: **enum은 API 스펙(`대성매출프로그램_API_스펙.md`) 기준으로 통일.** ShipmentType(NORMAL_SHIP/CONSIGN_SHIP/GIFT/TEACHER_USE/RETURN/CANCEL) · SalesCategory(SALE/FREE/RETURN) · WarehouseType(MAIN/CONSIGN). DB는 로컬 throwaway라 V1 직접 수정 + 볼륨 리셋으로 반영(V2 미사용).
  - **조건부(주석 처리)**: mssql-jdbc(DSLab/DSTxtBook), mysql-connector-j(DSRE2) — **Phase0 DSRE2 결정 후** 활성화.
- **패키지 구조(기능별 + 내부 layer 서브패키지)** — 이 규칙 유지:
  ```
  com.daesung.sales
   ├─ config/               (전역 스프링 설정: JpaAuditingConfig 등)
   ├─ common/entity/        (BaseEntity — 공용)
   └─ <기능>/               (partner, product, warehouse, inventory, consignment, salestype ...)
        ├─ entity/          (엔티티 + 그 기능의 enum)
        ├─ repository/      (Spring Data JPA)
        ├─ service/         (비즈니스 로직 — 예: product/service/ProductService. 내용 생길 때 추가)
        └─ controller/      (REST API — 내용 생길 때 추가)
  ```
  예: `product/entity/Product.java`, `product/repository/ProductRepository.java`, `product/service/ProductService.java`.
- **V1 스키마 완료**: `db/migration/V1__core_inventory.sql` + 엔티티9/리포지토리9/enum7. 상품·BOM / 창고·재고·재고이벤트 / 출고유형 룩업 / 위탁미결·부분정산 / 채번 시퀀스 / 감사컬럼. 불변식·음수방지·초과정산 CHECK 포함. **로직(서비스)은 아직 없음.**
- ⚠️ 앱 기동은 아직 불가(application.yml / DataSource 미설정). Flyway 실행·엔티티 검증은 DB 연결 후.

## 3. ★ 이 프로젝트의 대전제 (틀리기 쉬운 지점)
소스·DB 실측(W1-W2) 결과, 기획서의 여러 "확정"이 뒤집혔다. **"재현/이관 프로젝트"가 아니라 "핵심 도메인 신규 구축"이다.**

- **재고·매출·채권이 "자체 완결로 이미 구현돼 있다"는 전제는 틀렸다.** 창고·이중장부·위탁미결·BOM·월마감은 **레거시에 코드/컬럼이 전무** → 전면 신규. 레거시엔 단일 재고풀 이벤트기록 + 조회계산 채권만 있다.
- **레거시 스택은 "ASP"가 아니라 VB.NET WinForms(.NET4.8, ClickOnce)** = 2-tier 데스크톱 → 3-tier 웹 이전. (견적 전제조건 무효 → 재견적 트리거)
- **DSRE2는 "더프 전용 연동대상"이 아니다.** 교재·물류·학교까지 담는 **공용 운영 DB 허브(MySQL, 58테이블+저장함수 25)**. 매출프로그램·order가 모두 여기에 직접 read+write로 결합. **DSRE2 통합/분리 결정이 아키텍처 1순위.**
- 비즈니스 로직 소재: SQL Server 저장프로시저 **0건**, 클라이언트 인라인 SQL이 ~50개 폼에 산재. DSRE2 쪽에만 저장함수 25개.
- 데이터 모델 13개 세부축 중 **8 완전부재 / 3 부분 / 2 실재.**

용어: 판정은 시트에서 `✅확정 · ⚠️부분 · 🆕신규 · 🔁이식가능 · 🔴보안결함 · ⛔라이브데이터 필요`로 표기된다.

## 4. 3대 핵심 로직 (난이도의 대부분)
- **로직 A — 재고 엔진**: 일반입고/단순이고/BOM 조립·해체. **원자적 트랜잭션 + 음수재고 방지.** 위탁출고 저장 시 `단순이고(물류창고→위탁창고)`가 **한 트랜잭션으로 자동 생성**. 레거시엔 이고·BOM·트랜잭션/락 전부 없음 → 완전 신규.
- **로직 B — 위탁 미결정산**: 위탁출고 → 물류장부 즉시차감 + 재무장부 **미결**. 매출등록에서 **부분정산(분할) 누적**으로 확정. 불변식 `총출고 = 기정산 + 미결잔여`, **초과정산 방지**, 원본출고번호(OUT-) 역추적. **레거시 전량 부재 = 참고구현 없음, 최대 리스크. 과거 미결 데이터는 마이그레이션 복원 불가(cut-over부터).**
- **로직 C — 주문 상태머신**: 레거시에 **3개 독립 상태모델**이 실재(DSRE2 state / 매출프로그램 날짜플래그 / order STATE). **기획서의 한글 상태어휘는 코드에 리터럴로 없음** → 상태전이도는 **발주처 서면 승인**으로만 확정. 상태변경 이력 테이블도 없음.

검증된 이식가능 로직(초반에 빠르게 이관): **제품수불부 계산식**(`이월+입고−매출−교사용−반품+폐기±대체=현재재고`), **물류비 계산식**, **홈택스 계산서 엑셀 양식** — 전부 verbatim 확보.

## 5. 핵심 데이터 모델 축 (전부 신규 설계 대상)
- 상품: 단품↔세트(**BOM 가변비율**), 자체교재↔외부콘텐츠(**콘텐츠구분** 신규축, 외부는 매입 발생 → 순매출=매출−매입).
- 창고: 물류창고(실물)↔위탁창고(가상). 재고 = **상품×창고** 원장 + `inventory_txn`(7종).
- 출고유형(6)→회계구분(3) **룩업**(레거시는 tradeType 자유문자열+하드코딩 CASE). **취소는 공급률 30% 기준**으로 매출/무가 분기.
- 채권/여신: 미수금 러닝밸런스, 담보(여신한도 컬럼 신규), 수금/어음.
- 매출목표(대시보드용), RBAC(부서별), 감사컬럼(created_by/updated_by/modified_at 전 테이블 표준), **월마감 `period_locks`**(§7 갭 참고).
- 채번: 레거시 `Max+1`(동시성 없음) 폐기 → **시퀀스+분산락**. 전표 접두어 S(출고)/I(매출)/P(폐기)/OUT(위탁원본).

## 6. Phase 로드맵 (개발문서 기준)
- **Phase 0 · 사전조치**: 🔴 order IDOR 핫픽스 / 시크릿·계정 로테이션 / **DSRE2 통합·분리 결정(ADR)** / 마스킹 덤프 확보 / 과거 위탁미결 복원불가 서면합의 / 재견적 협의.
- **Phase 1 · 기반설계**: 신규 스키마(창고/위탁정산/BOM/감사/목표) / 채번(시퀀스+락) / RBAC·인증 / **상태전이도 발주처 승인** / API 계약(OpenAPI).
- **Phase 2 · 핵심로직**: 재고엔진 + 위탁정산 + 수불부 통합. 완전신규 = 별도 버퍼.
- **Phase 3 · 백오피스 화면**: 그리드 PoC 선행 → 주문출고/매출/마감/물류 화면군. (로직 완료된 순서로만 화면 착수)
- **Phase 4 · order 개편**: 스택전환 결정 / 신규메뉴 4종 / RBAC 적용.
- **Phase 5 · 리포트·대시보드(Phase1 병행)**: RDLC 13종 재현(**난이도 최상**, 나눔고딕 임베드) / 배치잡 / 대시보드 2종.
- **Phase 6 · 통합·마이그레이션·컷오버**: 법인별 분리유지 결정 / FK 클렌징 / 날짜 정규화 / DSRE2 프로시저 재구현 / 보안 재검증 / 부하테스트 / 컷오버.

개발 원칙 5가지: ① 보안결함은 정규사이클과 분리·즉시격리 ② 데이터모델 먼저·화면은 다음 ③ DSRE2 결정이 다수 화면의 선행 ④ 검증된 로직 먼저 이식·개발력은 신규(위탁/BOM/창고)에 집중 ⑤ 리포트 PoC 조기착수.

## 7. ⚠️ 착수 전 블로커 & 문서 자체의 알려진 갭
**Phase 0 결정 없이는 하위 설계 확정 불가** (특히 DSRE2 통합/분리 = 전체 일정 병목). 아래는 검토에서 확인된 **두 시트가 아직 안 메운/불일치** 항목 — 작업 중 마주치면 이 점을 상기할 것:

- **A. 기초 마스터(거래처/도서/학교/물류단가) 스키마·CRUD 화면이 스케줄에 없음.** 모든 로직의 FK 타깃인데 누락 → 창고만 만들고 도서·거래처 마스터가 비면 안 됨. 착수 시 명시적 태스크로 승격.
- **B. 매출 월마감(period_locks / 컨펌→수정잠금)이 개발문서에서 누락(회귀).** 로컬 기술문서엔 있었음. 모든 매출 쓰기 API에 횡단 잠금검사가 필요 → 나중에 넣으면 재작업 큼. **de-scope인지 유실인지 확인 필요.**
- **C. 보안 파라미터 바인딩 위치 자기모순**: BE 작업표는 맨 끝(순번20 "전체"), 리스크표·원칙①은 Phase0/1. **secure-by-default로 통일**(정적분석 Phase1 도입, 매 API 기본 적용, 최종감사만 순번20).
- **D. 100 vs 50(인원 vs 부수) 정의차** 검증이 매출일괄등록·DSRE 개발보다 늦음. 정의차면 "자동화로 해결 불가·요구 재정의". → 이 검증을 **매출일괄등록 착수 게이트**로.
- **E. DSRE2 어댑터가 매출 API보다 뒤(순번18 vs 15)** — 매출일괄등록이 어댑터에 의존하므로 순서 역전. 어댑터를 앞으로.
- **F. 세 시트(로드맵/BE/FE) 순번체계·의존성 불일치.** 동일 태스크 선행이 시트마다 다름(예: 창고스키마 dep). 정본 하나로 통일 필요.
- **G. 결손 미반영**: 재고실사(실물대조·차이조정), DSRE 요구 8건 트랙, order 개선 4건 일부(다중선택/담당자자동/전년도리셋).
- **H. 🔴 dsre2 재고 동기화가 외부 웹앱에 있음(최대 리스크)**: 레거시 SQL Server(DSSales)는 재고 잔고 미저장(계산). 그런데 `dsre2`(MySQL)는 잔고를 저장(`tbl_materials_info.CNT`, `tbl_booklist_cnt.STOCK`)하는데 **이 저장소엔 그걸 쓰는 코드가 없음** → 입고↔CNT 동기화 로직이 **repo에 없는 별도 웹앱**에 존재. 교재/더프 재고를 다루려면 그 로직/DB 규칙이 필요. **DSRE2 통합/분리(Phase0) + 재견적에 직결.**

## 8. 정보 출처 (정본 = 접근통제된 구글시트)
두 시트가 정본이며, 이 CLAUDE.md보다 우선한다. 세부·최신 확인 시 **다시 읽어라**.

| 문서 | 스프레드시트 ID | 탭 |
|---|---|---|
| **요구사항정의서 v1.1** | `1nbuDH84pmxluhLXwSQM8Us4G2sugP_4YSXEsU63MUd4` | 0.개요 / 1.화면별요구사항(29화면) / 2.데이터·업무로직 / 3.메뉴매핑 / 4.기술검증갭분석 / 5.오픈이슈(95건) |
| **개발문서** | `1yW2mL261n06rAu_uclWaqPtyvrmxYZy5Nly42vwtHak` | 0.개요 / 1.우선순위로드맵 / 2.백엔드작업순서 / 3.프론트작업순서 / 4.StageGate / 5.리스크20건 |

**읽는 법**: 서비스계정 키(`~/.config/google-sheets/service-account.json`, 읽기전용)로 Sheets API 직접 호출. Drive 폴더 ID `1zWwH-ehg53GhBwYj4x0ZtOseYMOHYAc0`.
```
python3 <script> --sheet <ID> --tab <탭명>   # scratchpad의 read_*.py 참고. 라이브러리: google-auth, requests
```
(gsheets MCP도 등록돼 있으나 **읽기전용**이라 시트 수정 불가 — 정정은 "메모"로 산출하고 반영은 사람이 직접.)

**레거시 원본 소스·DDL(참조자료 전량, gitignore됨)**: `reference/legacy/`
- **`DsSales-main/`** — VB.NET WinForms 매출프로그램 본체(.vb 121, rdlc 26). 재고/수불부/매출 로직 원본. 예: `제품수불부.vb`·`IC제품수불부.vb`·`기타고사_제품수불부.vb`·`도서입출고현황.vb`·`조회.vb`·`UC_TabPages_IC.vb`·`clsDB.vb`·`Common.vb`·`매출가져오기.vb`.
- **`dsdo-dealer-main/`** — order 사이트(Java Spring MVC, jsp). 특약점 신청/조회 로직.
- **`DSRE2-main/`** — DSRE2(C# 데스크톱).
- **`*.sql`** — DDL/프로시저(DSLab 18t / DSTxtBook 7t / dsre-table 58t / dsre-procedure 25루틴).
- **`대성매출프로그램_API_스펙.md`**(개발 API 스펙), **Work Process v1.5 PDF**(기획서 29화면).
- **활용법(★ 신규 기능 만들 때 항상)**: 해당 화면의 레거시 `.vb`를 먼저 `grep`/`Read`로 찾아 **inline SQL·계산식·분기 규칙을 확인한 뒤** 우리 구조로 재현. (레거시는 잔고 미저장·화면마다 계산식 상이 — 그대로 베끼지 말고 정본 공식 하나로 통일. [[재고 정의 단일화]])
- ⚠️ **참조자료지 우리 스키마/코드가 아님.** 크리덴셜·취약점 상세는 절대 커밋/노출 금지. **`src/main/resources/db/migration/`에 절대 두지 말 것**(Flyway 실행됨).

## 9. 작업 규칙 / 컨벤션
- **보안·비밀정보**: 레거시의 DB 호스트·계정·API키, order IDOR 등 **취약점 상세는 이 파일이나 커밋에 절대 넣지 말 것.** 정본 시트(접근통제)에만 둔다.
- **gitignore 됨(커밋 금지)**: 로컬 분석 `.md`(기술문서·체크리스트 4종), 서비스계정 키, `.claude/`. → `git status`에 이것들이 뜨면 add 하지 말 것.
- **이중 장부 불변**: 물류(재고)와 재무(매출)는 항상 별도 추적. 위탁출고는 재고 즉시차감 / 매출 미결.
- **트랜잭션 원자성**: 위탁출고+자동이고, 매출확정+미결차감, BOM 조립/해체는 각각 **한 트랜잭션**. 음수재고·초과정산은 제약으로 차단.
- **★ 재고 정의 단일화(레거시 #4 근절)**: 레거시는 재고 잔고를 저장 안 하고 화면마다 다른 공식으로 계산 → 같은 도서가 화면마다 재고 다름. 우리는 **`inventory_txn`(이벤트 로그)이 유일 진실**, 재고 = `SUM(qty) by 상품×창고`라는 **단 하나의 공식(=제품수불부)**. `inventory.qty`는 그 공식의 **재생성 가능한 캐시**일 뿐(언제든 txn에서 재계산·대사 가능). **화면마다 다른 재고 계산식 금지.**
- **재고 잔량 갱신 동시성**: read-modify-write 금지. **원자적 UPDATE** `qty = qty + :delta`(`InventoryRepository.addQty`, `@Modifying`)로 갱신 → 동시 갱신에도 lost update 없음(DB 행 잠금 직렬화). 최초 생성(행 없음) 경합은 (product_id, warehouse_id) UNIQUE가 방어. (병렬 20건 입고 검증: 정확히 누적됨. 참고로 파생쿼리 `@Lock`은 이 케이스에서 유실 재현돼 원자 UPDATE 채택.)
- **화면 선개발 금지**: 대응 백엔드 로직·스키마가 확정되기 전 화면 착수 금지(원칙②).
- **공통 API 응답 형식**(Claude 추가, 시트 미명시): 모든 컨트롤러는 `ApiResponse<T>`로 감싸 반환(`{success, data, error}`). 도메인 오류는 `throw new BusinessException(ErrorCode.XXX)` → `GlobalExceptionHandler`가 공통 실패 응답으로 변환. 에러코드는 `common/exception/ErrorCode`에 추가(예: PERIOD_LOCKED·NEGATIVE_STOCK·OVER_SETTLEMENT).
- **★ API 작성 규약(신규 API는 항상 이 형태로)**:
  - 경로: `/api/v1` 자동 prefix(WebConfig). 컨트롤러엔 `/masters/...` 처럼만 매핑.
  - 계층: `controller` → `service`(@Transactional) → `repository`. DTO는 각 기능 `dto/` 패키지(record 권장).
  - 응답: 성공 `ApiResponse.success(data)`, 목록 `ApiResponse.success(PageResponse.of(page))`. 오류는 `throw new BusinessException(ErrorCode.XXX)`.
  - **페이징 목록**: 파라미터로 `Pageable` 대신 **`PageRequestDto`(@ParameterObject)** 받고 `.toPageable()` 변환. (스프링 기본 Pageable은 Swagger 설명이 영어 하드코딩이라 회피)
  - **Swagger 한글 문서화 필수**: 컨트롤러 `@Tag(name,description)`, 메서드 `@Operation(summary,description)`, 요청 DTO 필드 `@Schema(description,example, requiredMode)`. → Swagger가 한글 설명+예시값 자동 노출.
  - 등록 검증: `@Valid` + 코드 중복 등 도메인 검증은 서비스에서 `BusinessException`.
  - **참고 템플릿**: `product`/`warehouse`/`partner` 패키지(상품·창고·거래처 CRUD)가 표준 예시. 새 API는 이걸 복제·변형.
  - 보안은 현재 **전체 허용**(SecurityConfig, RBAC 도입 시 제한 — TODO). Swagger는 `/swagger-ui/index.html`.
- 언어: 산출물·주석·커밋 메시지는 한국어 우선(팀 문서가 한국어).

## 10. 지금까지의 진행 (세션 컨텍스트)
- 로컬 분석문서(기술문서 v0.3 + W1-W2 체크리스트 3종) → 구글시트 2종으로 통합·발전됨. 시트가 더 최신.
- Claude가 두 시트를 검토 완료: 요구사항정의서(구분 라벨과 실측 불일치·월마감 누락·이슈 상태 과다표시 등), 개발문서(위 §7 A~G).
- ✅ 스택 확정 + build.gradle 반영 완료(Boot 3.4.5/Java21/QueryDSL/PostgreSQL, 컴파일 검증).
- 다음 후보 작업: (1) 두 시트 정합성 크로스체크표 (2) 세 시트 통합 의존성 그래프 (3) Phase 1 스키마(창고/재고 등) 실제 설계 착수 (4) 도메인 패키지 구조 잡기 + application.yml(DataSource/Flyway) 설정.
