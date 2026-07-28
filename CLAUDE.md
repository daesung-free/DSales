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
- **스택 확정**: Spring Boot **3.4.5** · Java **21** · Gradle **8.14** · JPA + **QueryDSL 5.1.0(jakarta)** · **MySQL 8**(발주처 지시로 PostgreSQL→MySQL8 전환 완료, 클라우드/새서버 [[db-mysql8-cloud-confirmed]]). 패키지 `com.daesung.sales`, 진입점 `SalesApplication.java`. 컴파일 검증 완료.
  - Gradle 래퍼는 8.14(Boot 3.4.x 공식 지원 라인). Java 21은 `foojay-resolver`(settings.gradle)로 자동 프로비저닝.
  - build.gradle 의존성: web·validation·actuator / data-jpa·mysql-connector·querydsl / flyway(스키마 마이그레이션) / security / poi-ooxml(엑셀) / springdoc(OpenAPI) / lombok / test(testcontainers-mysql, security-test).
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
- **DSRE2는 "더프 전용 연동대상"이 아니다.** 교재·물류·학교까지 담는 **공용 운영 DB 허브(MySQL, 58테이블+저장함수 25)**. 매출프로그램·order가 모두 여기에 직접 read+write로 결합. **✅ 통합/분리 = "분리(기존 유지)"로 확정**(사용자 확인 2026-07-22): **DSRE2는 재구축 안 함, 매출프로그램만 재구축.** DSRE 의존기능(매출일괄등록·물류비 인원산출)은 재구현 말고 **기존 라이브 DSRE2에 직접 연결**(second DataSource, mysql-connector 활성화)해 read + 제한적 write-back. [[dsre2-stays-existing]]
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
- 채번: 레거시 `Max+1`(동시성 없음) 폐기 → **`seq_registry` 테이블 + 행잠금**(MySQL8 시퀀스 부재 대체, `SequenceService.next()` @Transactional UPDATE행잠금+SELECT로 원자적). 전표 접두어 I(매출)/P(폐기)/OUT(위탁)/수금·실사. (정상출고는 별도 S전표 없이 I로 흡수.)

## 6. Phase 로드맵 (개발문서 기준)
- **Phase 0 · 사전조치**: 🔴 order IDOR 핫픽스 / 시크릿·계정 로테이션 / ~~DSRE2 통합·분리 결정~~ **✅ 분리(기존 유지)로 확정** → 남은 건 운영 DSRE2 접속정보·마스킹 덤프 확보 / 과거 위탁미결 복원불가 서면합의 / 재견적 협의.
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
- **B. 매출 월마감(period_locks) — ✅ 해결(구현 완료).** 3중 확인 결과 "de-scope 아닌 유실"로 판명(요구사항정의서 3대로직/DB-30 명시, PDF·스펙·개발문서엔 미하달). 월 단위 잠금 + 매출등록/취소·위탁정산·수금 횡단 검사(`PeriodLockService.assertNotLocked` → PERIOD_LOCKED) 구현·검증 완료(`/closing/periods`). ⚠️ 정책 세부(확정 권한자·재오픈 정책)만 발주처 확인 대상으로 남음.
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
| **요구사항정의서(최신, 2026-07)** | `1hSKeN6-6FYiG-Kv3u3wFVoVVoJeNexb_zSpoQbSaLvU` | 0.개요 / 1.화면별요구사항(**38화면**, 기초관리5·월별매출액명세서·계산서월별신고 추가) / 2.데이터·업무로직 / 3.메뉴매핑 / 4.기술검증갭분석 (오픈이슈는 2탭 내 [10xx]행에 통합) |
| **개발문서(최신)** | `1rSO92iIBeN8JIUPmsbTp9uBNgRLKhJJn` | ⚠️**업로드 xlsx(미변환)라 Sheets API로 못 읽음** — Google Sheets로 변환 or Drive API로 xlsx 다운로드 필요 |
| (구버전 참고) 요구사항 v1.1 | `1nbuDH84pmxluhLXwSQM8Us4G2sugP_4YSXEsU63MUd4` | 29화면 시절. 최신은 위 |

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
- **공통 API 응답 형식**(Claude 추가, 시트 미명시): 모든 컨트롤러는 `ApiResponse<T>`로 감싸 반환(`{success, data, error}`). 도메인 오류는 `throw new BusinessException(ErrorCode.XXX)` → `GlobalExceptionHandler`가 공통 실패 응답으로 변환. 에러코드는 `common/exception/ErrorCode`에 추가(예: PERIOD_LOCKED·NEGATIVE_STOCK·OVER_SETTLEMENT). **예외: 파일 다운로드(예: 계산서 xlsx `TaxController.export`)는 `ResponseEntity<byte[]>`로 반환**(바이너리라 래핑 안 함).
- **★ API 작성 규약(신규 API는 항상 이 형태로)**:
  - 경로: `/api/v1` 자동 prefix(WebConfig). 컨트롤러엔 `/masters/...` 처럼만 매핑.
  - 계층: `controller` → `service`(@Transactional) → `repository`. DTO는 각 기능 `dto/` 패키지(record 권장).
  - 응답: 성공 `ApiResponse.success(data)`, 목록 `ApiResponse.success(PageResponse.of(page))`. 오류는 `throw new BusinessException(ErrorCode.XXX)`.
  - **페이징 목록**: 파라미터로 `Pageable` 대신 **`PageRequestDto`(@ParameterObject)** 받고 `.toPageable()` 변환. (스프링 기본 Pageable은 Swagger 설명이 영어 하드코딩이라 회피)
  - **Swagger 한글 문서화 필수**: 컨트롤러 `@Tag(name,description)`, 메서드 `@Operation(summary,description)`, 요청 DTO 필드 `@Schema(description,example, requiredMode)`. → Swagger가 한글 설명+예시값 자동 노출.
  - 등록 검증: `@Valid` + 코드 중복 등 도메인 검증은 서비스에서 `BusinessException`.
  - **참고 템플릿**: `product`/`warehouse`/`partner` 패키지(상품·창고·거래처 CRUD)가 표준 예시. 새 API는 이걸 복제·변형.
  - 보안: **인증 필수(JWT)**. 로그인/토큰재발급/부트스트랩·Swagger·`/actuator/health`만 공개, 그 외 인증 요구. 새 API는 기본적으로 인증 하에 동작(테스트 시 `POST /auth/login`으로 토큰 받아 `Authorization: Bearer` 헤더). **역할별 세부 권한 매트릭스(마감=FINANCE 등)는 발주처 확정 후 경로/@PreAuthorize로 확장** — 현재는 "인증된 사용자면 허용"까지. `created_by/updated_by`는 로그인 사용자 자동. Swagger는 `/swagger-ui/index.html`.
- 언어: 산출물·주석·커밋 메시지는 한국어 우선(팀 문서가 한국어).

## 10. 지금까지의 진행 (세션 컨텍스트) — 실측 최신(스키마 V1~V13, git 52커밋)

**인프라**: **MySQL 8(sales)** + Redis(refresh·캐시) + DSRE2 MySQL/MariaDB 복제본(docker `sales-dsre-mysql:3307`, 볼륨 `sales-dsredata`에 영속). 로컬 테스트는 **DB만 docker, 앱은 `./gradlew bootRun` (테스트 포트 8081)**. DSRE 기능은 `--daesung.dsre.enabled=true`로 켬(기본 off).

**✅ 구현+E2E검증 완료 (도메인별)**
- 공통: ApiResponse/예외/페이징/한글Swagger/채번시퀀스/Flyway
- 마스터: 거래처·도서·창고·BOM CRUD (+담보·세무필드). **32p 확장(V17)**: 도서=매출구분·**수불부노출**(제품수불부 집계 필터 연동)·Web게시여부 / 창고=**실물재고여부**(CONSIGN 기본 false)·**소속거래처**(위탁창고 FK) / **거래처별 단가·노출 매핑(V19 `product_partner_price`, 도서관리 3번째 탭)**: 도서×거래처 공급률+노출여부, 단가=정가×공급률 파생, upsert/자동조회(매출등록 단가조회용)/삭제 CRUD(`/masters/products/{id}/partner-prices/{partnerId}`). ⚠️도서 상품년도·상품구분, BOM 자재구분은 미구현(후속)
- **재고엔진(로직A)**: 입고·이고·BOM조립해체·폐기·**제품수불부(단일공식+대사)**·원자적UPDATE 동시성·**재고실사(ADJUST)**·**재고관리여부(V18 `stock_managed`, false=모의고사 등 인원기반 → 매출 시 재고 미차감. 근거: 레거시 모의고사는 invenData 미기록·수불부 없음)**
- 매출: 등록·**취소(재고복구 역분개)**·조회·순매출집계·**정상출고 재고연동**·**콘텐츠구분 순매출(외부콘텐츠 매입원가=매입입고 unit_cost 평균→이익률)**
- **입고구분(V15 `inbound_type` NORMAL/PURCHASE)**: 8p 입고등록에 매입입고 구분값(재무팀 정정 2026-07-28) → 매입입고로 등록된 입고만 16p 순매출조회 매입액으로 자동 집계(정상입고=인쇄소 등은 제외). 별도 매입 업로드 화면·파서 없음(범위 축소). 통합테스트 회귀 고정.
- **반품입고(29p, `POST /sales/return-inbound`)**: 물류 진입점 — 반품 사실(거래처·도서·수량·공급률)만 입력하면 한 트랜잭션으로 매출 반품(RETURN) 라인 자동생성 + 물류창고 재고 +복구(재고 잔량은 자동 산출). 재고관리 상품만 복구, 원본출고번호(선택) 역추적. 재무팀 확정(2026-07-25). ⚠️위탁 반품 역-자동이고는 후속(위탁 회계기준 회신 걸림).
- **위탁 미결정산(로직B)**: 출고자동이고·미결조회·부분정산(불변식·초과정산방지)
- 마감관리: 수금·미수금현황(담보비율)·외상매출장·이월스냅샷(idempotent) / **수익신고**(⚠️MySQL `TO_CHAR`→`DATE_FORMAT` 버그 수정+회귀테스트)·**계산서신고(홈택스 xlsx export)** / **계산서·세금계산서 월별신고(38p, `/closing/tax-filing` — 월×발행유형[계산서=면세tax0/세금계산서=과세tax≠0], 매출·반품·순매출·세액. 발행유형=tax 파생, 신규필드 없음. 미발행분=0 placeholder[정의 미확정])** / **계산서 10일 분기(`/closing/invoice-adjustments` — 반품 처리일 ≤10일=당월 수정발행(AMEND)/>10일=익월 마이너스(NEXT_MONTH_MINUS)+반영월·방식별합계. 재무팀 확정 2026-07-25. ⚠️반영 신고월 세부는 발주처 확인)** / **월마감(period_locks 횡단잠금)**
- **인증/RBAC**: JWT+**Redis refresh(회전)**·BCrypt·역할5종(ADMIN/FINANCE/LOGISTICS/SALES/VIEWER)·**권한 매트릭스**(경로·메서드)·감사 자동채움. 부트스트랩 admin/Admin1234! (로컬)
- Redis 캐시: 출고유형룩업·월마감상태(@Cacheable/@CacheEvict)
- **DSRE 연동(분리 확정)**: DsreGateway(호출만) + **인원산출·물류비 출고금액·매출일괄등록(state='T' write-back + 소스키 멱등)**
- **대시보드**: 매출목표 마스터(V12) + 목표대비 실적·달성률·전년비
- **★상품 카테고리축(V13 `cat_code`/`cat_name`)**: 계층 분류코드(첫 글자=대분류). 매출 리포트 5종의 공유 축. CRUD 노출. ⚠️ **실 데이터 출처 미정**(현재 수동입력만) — 이슈2 참고.
- **매출 리포트 9종(레거시 재현 + 재무팀 신규 2, `sale`/`inventory` 재사용)**:
  - 매출액명세서(`/sales/statement`): 분류 3계층 rollup(대분류→분류→도서), 합계=금액+세액
  - 거래명세서(`/sales/transaction-statement`): 공급자(SupplierProperties 단일법인)·공급받는자(거래처)·유가/무가 분리
  - 과목별매출현황(`/sales/category-summary`): 거래처×분류×도서 매출/반품/교사용 수량·반품률
  - 도서입출고현황(`/sales/book-inout`): **매입(inventory_txn 원가)+매출(sales 공급가) 이중장부 종합** + 정본 재고(SUM txn)·매출총이익. 취소=INBOUND 음수(현재 미모델링→0)
  - 거래처별 매출대비표(`/sales/yoy-comparison`): 당해 vs **전년 동기간**(salesDate 기준) 증감·비율, groupBy PARTNER/CATEGORY/BOOK
  - 물류비 회수·기간집계(`/logistics-costs`): 출고(기간·구분)+회수(반품/사고), DSRE
  - **월별매출액명세서(37p, `/sales/monthly-statement`)**: 구분(대분류)×상품 **성적처리/비처리 인원·금액**+계+과세매출·부가세, 대분류계·총계. 인원=수량, 성적처리=`sale.proc_type`(V16, GRADED/미지정=비처리). 매출(SALE)만. ⚠️DSRE 모의고사 자동 인원/처리 import는 후속(현재 수기등록)
  - **계산서·세금계산서 월별신고(38p, `/closing/tax-filing`)**: 월×발행유형(계산서=면세tax0/세금계산서=과세tax≠0) 매출·반품·순매출·세액. 발행유형=tax 파생(신규필드 없음). 미발행분=0 placeholder
- **✅테스트(회귀 방어)**: 매출 리포트 통합테스트 **10건**(Testcontainers MySQL8+Redis, `IntegrationTestSupport` 베이스, 37p·38p·수익신고 포함). `./gradlew test` → 11통과. ⚠️ Docker 29 대응 위해 TC **1.21.3** override(build.gradle).

**⬜ 미구현(결정 없이 가능)**: 정산내역서(위탁정산 내역, ⏸위탁 회계기준 회신 걸림) / 리포트 데이터 API 잔여(응시·회차현황=DSRE 의존) / 매출액명세서 등 리포트의 **프론트 RDLC 렌더링(나눔고딕, 백엔드 밖)** / 마이그레이션 스크립트(Phase6)

**⏸ 회신 대기(발주처·재무팀)**: 상태머신(상태전이도 승인=하드블로커) / **catCode 데이터 출처·연도 정의(이슈2·3 — 리포트 5종 급소: 레거시는 catCode에 연도 인코딩, 우리는 자유문자열)** / 위탁 회계기준 적정성 / ~~매입원가 출처~~ **✅해소(=매입입고 unit_cost, 8p 구분값→16p 자동연결, 재무팀 확정 2026-07-28)** — 남은 건 이슈#57(외부콘텐츠 매입액B가 매입반품 차감 순액인지, 연말엣지) / 여신한도(별개?) / 수금 건별매칭 / **법인 축(멀티테넌트: 현재 단일법인 가정 — 월마감·계산서·거래명세서 공급자)** / 세금 반올림(현재 버림) / 취소 30% 재분류(우리 모델이 이미 커버).

**규율(반드시 지킬 것)**: 신규 기능은 착수 전 **레거시 `.vb` + 정본 시트 3중 확인**(mis-scope 방지 — 물류비가 DSRE 의존인 걸 이렇게 잡음) → 만들고 → **로컬 E2E(로그인 토큰 포함) 검증** → 커밋(한국어, 검증 요약 포함). **핵심 로직·리포트는 통합테스트로 회귀 고정**(`IntegrationTestSupport` 상속, Testcontainers). DSRE 픽스처는 scratchpad(비커밋), 데이터는 docker 볼륨에 영속.
