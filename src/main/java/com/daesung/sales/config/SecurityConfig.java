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
 *
 * <p>★<b>권한 판정은 이 파일에 없다.</b> 화면별 권한 표(V52)를 읽는
 * {@code DynamicAuthorizationManager}가 판정한다 — 발주처 요구가
 * "관리자가 운영 중 화면/필드 단위 권한을 자유롭게 조정"이라 코드에 박을 수 없다.
 * 여기 남은 것은 <b>표 밖에 있어야 하는 것</b>뿐이다: 공개 경로와, 표 자체를 바꾸는 경로.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, JwtProvider jwtProvider,
            com.daesung.sales.permission.service.DynamicAuthorizationManager dynamicAuthorizationManager,
            com.daesung.sales.audit.service.AccessDeniedLogger accessDeniedLogger)
            throws Exception {
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
                        // 계정 생성·권한 관리는 관리자만(권한 표 자체를 바꾸는 경로라 표 밖에 둔다 —
                        // 표를 잘못 고쳐 스스로를 잠그면 되돌릴 길이 없어진다)
                        .requestMatchers("/api/v1/auth/users", "/api/v1/auth/users/**").hasRole("ADMIN")
                        // ‼️본인 권한 조회만 예외다. 관리자가 정한 화면 권한을 역할 계정이
                        //   읽지 못하면 프론트가 메뉴를 그 설정대로 그릴 수 없어,
                        //   화면마다 권한표를 따로 들고 있다가 관리자 설정과 어긋난다.
                        //   ★읽기 전용이고 자기 것만 나간다 — 남의 권한·매트릭스 전체는 여전히 관리자만.
                        .requestMatchers("/api/v1/permissions/me").authenticated()
                        .requestMatchers("/api/v1/permissions/**").hasRole("ADMIN")
                        // 행위기록은 관리자만 — 남의 다운로드 기록이 아무나 보이면 그 자체가 감시로 읽힌다.
                        .requestMatchers("/api/v1/audit/access-log/**").hasRole("ADMIN")
                        // 자기 계정 관련은 역할과 무관하게 인증만 되면 허용.
                        // ‼️로그아웃은 쓰기(POST)라, 권한 표에 /auth/** 화면이 없으면
                        //   deny-by-default에 걸려 403이 된다 — 로그인한 사람이 로그아웃을
                        //   못 하는 상태였다(전 API 점검에서 잡힘). 권한으로 통제할 대상이 아니다.
                        .requestMatchers("/api/v1/auth/logout", "/api/v1/auth/me").authenticated()
                        // ── 그 외 전부: 권한 표에서 판정한다 ──────────────────────────────
                        // 예전엔 여기에 경로별 hasRole(...)이 스무 줄 나열돼 있었다.
                        // 발주처 요구(2026-08-21 ①)가 "화면/필드 단위 권한을 관리자가 운영 중
                        // 자유롭게 조정"이라, 코드에 박아 두면 배포 없이는 못 바꾼다.
                        // 화면에 붙지 않은 경로의 쓰기는 매니저가 거부한다(deny-by-default 유지).
                        .anyRequest().access(dynamicAuthorizationManager))
                // 미인증=401(로그인 필요), 인증됐으나 권한부족=403.
                // ★setStatus 사용(sendError 아님) — sendError는 ERROR 재디스패치를 유발,
                //   그 재디스패치엔 JWT 필터(OncePerRequestFilter)가 안 돌아 익명 재평가로 401이 덮어씀.
                // ★403은 AccessDeniedLogger가 받는다 — 상태를 세우고 access_log에 남긴다.
                //   예전엔 여기 람다가 상태만 세워서, 권한 위반 시도가 감사기록에 한 줄도
                //   남지 않았다(409·401은 남는데 403만 빠짐 — 2026-09-11 점검).
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler(accessDeniedLogger))
                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
