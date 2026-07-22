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
        return new JdbcTemplate(ds);
    }
}
