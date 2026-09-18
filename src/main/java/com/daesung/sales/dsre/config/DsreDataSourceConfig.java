package com.daesung.sales.dsre.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * DSRE2 전용 JdbcTemplate. daesung.dsre.enabled=true일 때만 생성.
 * ★DataSource를 스프링 빈으로 노출하지 않음(내부 생성) → 기존 Postgres 기본 DataSource 자동설정 유지.
 */
@Configuration
@ConditionalOnProperty(name = "daesung.dsre.enabled", havingValue = "true")
public class DsreDataSourceConfig {

    /**
     * DSRE2 전용 트랜잭션 경계.
     *
     * <p>★<b>왜 필요한가</b> — 여기 {@code DataSource}는 스프링 빈이 아니라(아래 주석 참고)
     * 기본 트랜잭션 매니저가 관리하지 않는다. 그래서 서비스에 {@code @Transactional}을 붙여도
     * <b>DSRE2 쪽은 묶이지 않는다</b>. 주문 등록처럼 <b>테이블 셋을 한 번에</b> 넣어야 하는
     * 작업이 중간에 실패하면, 헤더만 들어가고 반·수량이 빠진 <b>반쪽 주문</b>이 남는다.
     *
     * <p>{@code TransactionTemplate}만 노출하고 DataSource는 계속 감춘다 —
     * 빈으로 내놓으면 기본 JPA 자동설정이 그걸 주 DataSource로 집어갈 수 있다.
     */
    @Bean
    public org.springframework.transaction.support.TransactionTemplate dsreTransactionTemplate(
            JdbcTemplate dsreJdbcTemplate) {
        var tm = new org.springframework.jdbc.datasource.DataSourceTransactionManager(
                java.util.Objects.requireNonNull(dsreJdbcTemplate.getDataSource()));
        return new org.springframework.transaction.support.TransactionTemplate(tm);
    }

    @Bean
    public JdbcTemplate dsreJdbcTemplate(DsreProperties props) {
        HikariDataSource ds = new HikariDataSource();
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setJdbcUrl(props.url());
        ds.setUsername(props.username());
        ds.setPassword(props.password());
        ds.setReadOnly(props.readOnly());     // 라이브 안전: 조회/함수호출 위주
        ds.setMaximumPoolSize(3);
        ds.setPoolName("dsre-pool");

        // ★유휴 연결을 붙들지 않는다(minimumIdle=0).
        //   DSRE는 몇몇 화면에서만, 그것도 띄엄띄엄 쓴다. 기본값은 minIdle=maxPoolSize라
        //   연결 3개를 몇 시간이고 놀리는데, 그동안 MariaDB가 wait_timeout 으로 그걸 끊는다.
        //   다음 요청에서 Hikari가 죽은 연결 3개를 발견하고 버리면서 로그에 경고가 3줄씩 남았다
        //   (운영 로그 실측: "dsre-pool - Failed to validate connection ... connection closed"
        //    버스트가 정확히 3줄씩, 몇 시간 간격으로 반복).
        //   요청이 실패한 건 아니지만 유휴 후 첫 요청이 느려지고 로그가 더러워진다.
        //
        //   ‼️maxLifetime 만 줄이는 방식은 쓰지 않는다 — 그러면 DB의 wait_timeout 값을
        //     우리가 알고 있어야 하고, 그 값이 바뀌면 조용히 다시 깨진다.
        //     유휴 연결 자체를 두지 않으면 wait_timeout 이 얼마든 무관하다.
        ds.setMinimumIdle(0);
        ds.setIdleTimeout(60_000);        // 1분 놀면 반납 — 다시 필요하면 새로 연다
        ds.setMaxLifetime(600_000);       // 오래 쓰는 연결도 10분에서 교체(이중 안전장치)
        ds.setKeepaliveTime(120_000);     // 살아 있는 동안엔 2분마다 핑
        ds.setConnectionTimeout(10_000);  // 복제본이 죽었을 때 요청이 오래 매달리지 않게
        ds.setConnectionInitSql("SET NAMES utf8mb4"); // 한글 자재명 비교 정확성(연결 문자셋 고정)
        return new JdbcTemplate(ds);
    }
}
