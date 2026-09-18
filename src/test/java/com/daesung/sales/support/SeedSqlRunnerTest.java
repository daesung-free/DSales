package com.daesung.sales.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 담당자에게 건네는 시드 SQL이 <b>실제로 통하는지</b> 돌려 본다.
 *
 * <p>★<b>왜 테스트로 두는가</b> — 컬럼을 추측해 적어 보내면 DBeaver 에서 깨지고,
 * 그 시간은 받는 사람이 쓴다. 우리 마이그레이션이 그대로 적용된 컨테이너에서 한 번 돌려
 * "붙여넣으면 된다"는 것을 확인한 뒤 건넨다.
 *
 * <p>스키마가 바뀌어 시드가 깨지면 <b>여기서 먼저 깨진다</b> — 건넨 파일이 조용히 낡는 것을 막는다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("시드 SQL 실행 가능성")
class SeedSqlRunnerTest extends IntegrationTestSupport {

    /**
     * 담당자에게 그대로 건네는 파일. <b>사본을 두지 않는다</b> — 두 벌이 되면 한쪽만 고쳐지고,
     * 건넨 쪽이 낡았는지 아무도 모른다. 여기가 원본이고 이 파일을 열어 복사해 보낸다.
     *
     * <p>{@code db/migration}이 아니라 테스트 리소스에 둔다 — Flyway가 집어 배포마다 돌면
     * 시험 데이터가 실사용 DB에 계속 들어간다.
     */
    private static final Path SEED = Path.of("src/test/resources/seed/seed-260918.sql");

    @Autowired
    private JdbcTemplate jdbc;

    /** 주석·빈 줄을 걷어내고 세미콜론으로 끊는다. 되돌리기 블록은 전부 주석이라 자동으로 빠진다. */
    private static List<String> statements(String sql) {
        StringBuilder cur = new StringBuilder();
        List<String> out = new ArrayList<>();
        for (String line : sql.split("\n")) {
            String l = line.strip();
            if (l.isEmpty() || l.startsWith("--")) {
                continue;
            }
            cur.append(' ').append(l);
            if (l.endsWith(";")) {
                out.add(cur.toString().strip().replaceAll(";$", ""));
                cur.setLength(0);
            }
        }
        return out;
    }

    /**
     * ★<b>끝나면 지운다.</b> 통합테스트는 컨테이너 DB를 클래스끼리 공유해서,
     * 시드 매출 7건을 남겨 두면 전역 합계를 보는 다른 테스트가 조용히 깨진다.
     * 파일 맨 아래 「되돌리기」와 같은 순서로 지운다 — 그 블록이 실제로 통하는지도 같이 확인된다.
     */
    @AfterAll
    void 되돌린다() {
        jdbc.execute("DELETE FROM sales WHERE memo LIKE '[SEED]%'");
        jdbc.execute("DELETE FROM sales_target WHERE product_id IN"
                + " (SELECT id FROM products WHERE code LIKE 'SEED-%')");
        jdbc.execute("DELETE FROM material_bom WHERE material_id IN"
                + " (SELECT id FROM materials WHERE code LIKE 'SEED-%')");
        jdbc.execute("DELETE FROM materials WHERE code LIKE 'SEED-%'");
        jdbc.execute("DELETE FROM products WHERE code LIKE 'SEED-%'");
        jdbc.execute("DELETE FROM schools WHERE school_code LIKE 'SEED-%'");
        jdbc.execute("DELETE FROM partners WHERE code LIKE 'SEED-%'");
    }

    /** 시드 거래처로 범위를 좁힌다 — 다른 테스트가 넣은 매출이 섞이면 합이 안 맞는다. */
    private long seedPartnerId() {
        return jdbc.queryForObject(
                "select id from partners where code = 'SEED-P01'", Long.class);
    }

    @Test
    @DisplayName("★붙여넣으면 그대로 돈다 — 컬럼·제약이 다 맞는다")
    void 시드가_돈다() throws Exception {
        if (!Files.exists(SEED)) {
            return;   // 시드 파일은 비커밋(docs-local)이라 없을 수 있다
        }
        for (String stmt : statements(Files.readString(SEED))) {
            if (stmt.toUpperCase().startsWith("SELECT")) {
                jdbc.queryForList(stmt);          // 확인 쿼리
            } else {
                jdbc.execute(stmt);
            }
        }

        assertThat(jdbc.queryForObject(
                "select count(*) from materials where code like 'SEED-%'", Integer.class))
                .as("자재 6종").isEqualTo(6);
        assertThat(jdbc.queryForObject(
                "select count(*) from sales where memo like '[SEED]%'", Integer.class))
                .as("매출 7건").isEqualTo(7);
        assertThat(jdbc.queryForObject(
                "select count(*) from sales_target st join products p on p.id = st.product_id"
                        + " where p.code like 'SEED-%'", Integer.class))
                .as("상품 3종 × 12개월").isEqualTo(36);
    }

    @Test
    @DisplayName("★시드가 실제로 화면 조회에 잡힌다 — 넣어도 안 보이면 의미가 없다")
    void 조회에_잡힌다() throws Exception {
        if (!Files.exists(SEED)) {
            return;
        }
        for (String stmt : statements(Files.readString(SEED))) {
            if (!stmt.toUpperCase().startsWith("SELECT")) {
                jdbc.execute(stmt);
            }
        }
        token();
        int year = java.time.LocalDate.now().getYear();

        // 자재 마스터 — 목록은 페이지가 아니라 배열로 온다
        assertThat(data(get("/masters/materials?keyword=SEED-M")))
                .as("자재 목록에 뜬다").hasSize(6);

        // 응시현황 — 분류코드 M+A 계열로 넣었으니 잡혀야 한다
        // ‼️합계는 TOTAL 행에서 읽는다 — 행에 소계·총계가 섞여 있어 전부 더하면 두 배가 된다.
        JsonNode att = data(get("/sales/attendance-period?fromDate=" + year + "-01-01"
                + "&toDate=" + year + "-12-31&partnerId=" + seedPartnerId()));
        long total = 0;
        long graded = 0;
        for (JsonNode r : att.path("rows")) {
            if ("TOTAL".equals(r.path("rowType").asText())) {
                total = r.path("total").asLong();
                graded = r.path("gradedTotal").asLong();
            }
        }
        assertThat(total).as("응시현황 총계(모의고사 6건 합)").isEqualTo(700);
        assertThat(graded).as("성적처리 인원(3건 합)").isEqualTo(560);

        // 통합매출조회 — 학교코드·회차 칸이 실제로 찬다
        JsonNode sales = data(get("/sales?fromDate=" + year + "-01-01&toDate=" + year
                + "-12-31&keyword=SEED&size=50"));
        boolean hasSchool = false;
        boolean hasRound = false;
        for (JsonNode r : sales.path("content")) {
            hasSchool |= r.hasNonNull("schoolCode");
            hasRound |= r.hasNonNull("bookRound");
        }
        assertThat(hasSchool).as("학교코드 칸이 찬다").isTrue();
        assertThat(hasRound).as("회차 칸이 찬다").isTrue();
    }
}
