package com.daesung.sales.config;

import com.daesung.sales.auth.jwt.JwtAuthenticationFilter;
import com.daesung.sales.auth.jwt.JwtProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 보안 설정(RBAC). stateless JWT. 로그인·Swagger는 공개, 그 외 인증 필수.
 * 역할별 세부 권한 매트릭스(마감=재무 등)는 발주처 확정 후 경로/@PreAuthorize로 확장.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtProvider jwtProvider) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 공개: 로그인/토큰재발급/최초관리자, API 문서, 헬스
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/bootstrap-admin").permitAll()
                        .requestMatchers(
                                "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**",
                                "/actuator/health").permitAll()
                        // 계정 생성은 관리자만
                        .requestMatchers("/api/v1/auth/users").hasRole("ADMIN")
                        // ── 쓰기(변경) = 도메인 역할. ★기본안 — 정확한 매트릭스는 발주처 확정 대기 ──
                        // 기초관리(마스터): 관리자
                        .requestMatchers(HttpMethod.POST, "/api/v1/masters/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/masters/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/masters/**").hasRole("ADMIN")
                        // 재고/물류: 물류
                        .requestMatchers(HttpMethod.POST, "/api/v1/stock/**", "/api/v1/disposals/**")
                                .hasAnyRole("LOGISTICS", "ADMIN")
                        // 매출/위탁: 영업
                        .requestMatchers(HttpMethod.POST, "/api/v1/sales/**", "/api/v1/consignment/**")
                                .hasAnyRole("SALES", "ADMIN")
                        // 마감/채권/세무: 재무
                        .requestMatchers(HttpMethod.POST, "/api/v1/closing/**").hasAnyRole("FINANCE", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/closing/**").hasAnyRole("FINANCE", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/closing/**").hasAnyRole("FINANCE", "ADMIN")
                        // 그 외(대부분 GET 조회) = 인증된 사용자면 허용(VIEWER 포함)
                        .anyRequest().authenticated())
                // 미인증=401(로그인 필요), 인증됐으나 권한부족=403.
                // ★setStatus 사용(sendError 아님) — sendError는 ERROR 재디스패치를 유발,
                //   그 재디스패치엔 JWT 필터(OncePerRequestFilter)가 안 돌아 익명 재평가로 401이 덮어씀.
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((req, res, ex) -> res.setStatus(HttpStatus.FORBIDDEN.value())))
                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
