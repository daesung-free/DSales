package com.daesung.sales.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI(Swagger) 문서 설정. UI: /swagger-ui.html, 스펙: /v3/api-docs. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI salesOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("대성 매출프로그램 API")
                .description("매출프로그램 재구축 백오피스 REST API")
                .version("v0.0.1"));
    }
}
