package com.daesung.sales.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI(Swagger) 문서 설정. UI: /swagger-ui.html, 스펙: /v3/api-docs. JWT Bearer 인증 스킴 포함. */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI salesOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("대성 매출프로그램 API")
                        .description("매출프로그램 재구축 백오피스 REST API. "
                                + "인증: POST /auth/login → accessToken → 우측 상단 Authorize에 입력.")
                        .version("v0.0.1"))
                // 우측 상단 Authorize 자물쇠 + 모든 요청에 Bearer 토큰 자동 첨부
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("로그인으로 받은 accessToken 입력(‘Bearer’ 접두어 없이)")));
    }
}
