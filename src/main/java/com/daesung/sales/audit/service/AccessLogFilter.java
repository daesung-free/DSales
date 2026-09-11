package com.daesung.sales.audit.service;

import com.daesung.sales.audit.entity.AccessAction;
import com.daesung.sales.audit.entity.AccessLog;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 사용자 행위를 {@code access_log}에 남긴다.
 * 근거: 발주처 요청(2026-09-11) "직원의 다운로드기록같은걸 볼 수 있게".
 *
 * <p>★<b>무엇을 남기지 않는지가 더 중요하다.</b>
 * <ul>
 *   <li><b>조회(GET 목록)는 안 남긴다</b> — 목록 한 번 열 때마다 한 줄이 쌓이면 하루 수만 건이 되고,
 *       정작 봐야 할 다운로드 기록이 그 안에 묻힌다(발주처 선택 B).
 *       단 <b>다운로드는 GET이어도 남긴다</b> — 파일이 밖으로 나간 것이라 이 표의 존재 이유다.</li>
 *   <li><b>요청 본문은 안 남긴다</b> — 비밀번호·토큰이 그대로 들어 있다.
 *       쿼리스트링도 민감한 이름은 지우고 남긴다.</li>
 *   <li><b>실패도 남긴다</b> — 막힌 접근 시도가 성공보다 중요할 때가 있다.</li>
 * </ul>
 *
 * <p>★기록이 요청을 망가뜨리지 않는다. 여기서 나는 예외는 삼키고 서버 로그로만 알린다.
 */
@Component
@Order(Integer.MAX_VALUE)   // 응답이 다 만들어진 뒤 상태·헤더를 봐야 한다
@RequiredArgsConstructor
public class AccessLogFilter extends OncePerRequestFilter {

    static final String API = "/api/v1";

    private final AccessLogService accessLogService;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        long started = System.currentTimeMillis();
        try {
            chain.doFilter(req, res);
        } finally {
            try {
                write(req, res, (int) (System.currentTimeMillis() - started));
            } catch (RuntimeException e) {
                // 기록 실패가 응답을 건드리면 안 된다. 서비스가 이미 로그를 남긴다.
                logger.warn("접근로그 처리 중 오류(요청은 정상 종료)", e);
            }
        }
    }

    private void write(HttpServletRequest req, HttpServletResponse res, int tookMs) {
        String path = req.getRequestURI();
        if (path == null || !path.startsWith(API)) {
            return;   // 정적 리소스·문서는 대상이 아니다
        }
        AccessAction action = actionOf(req, res, path);
        if (action == null) {
            return;
        }
        String disposition = res.getHeader(HttpHeaders.CONTENT_DISPOSITION);
        accessLogService.record(AccessLog.of(
                username(), role(), action, menuOf(path),
                req.getMethod(), trim(path, 300), safeQuery(req.getQueryString()),
                res.getStatus(),
                fileNameOf(disposition),
                (action == AccessAction.DOWNLOAD) ? sizeOf(res) : null,
                clientIp(req), trim(req.getHeader(HttpHeaders.USER_AGENT), 300), tookMs));
    }

    /** 기록 대상 판정. 대상이 아니면 null. */
    private static AccessAction actionOf(HttpServletRequest req, HttpServletResponse res, String path) {
        if (res.getHeader(HttpHeaders.CONTENT_DISPOSITION) != null) {
            return AccessAction.DOWNLOAD;   // GET이어도 파일이 나갔으면 남긴다
        }
        if (path.startsWith(API + "/auth/login")) {
            return AccessAction.LOGIN;
        }
        if (path.startsWith(API + "/auth/logout")) {
            return AccessAction.LOGOUT;
        }
        // 토큰 재발급은 화면 뒤에서 30분마다 자동으로 일어난다 — 사람의 행위가 아니라 소음이다.
        if (path.startsWith(API + "/auth/refresh")) {
            return null;
        }
        // HTTP 메서드는 사양상 대문자 ASCII다. 로케일 의존 변환을 피하려고 그대로 비교한다.
        return switch (req.getMethod()) {
            case "POST", "post" -> AccessAction.CREATE;
            case "PUT", "PATCH", "put", "patch" -> AccessAction.UPDATE;
            case "DELETE", "delete" -> AccessAction.DELETE;
            default -> null;   // GET 조회는 남기지 않는다
        };
    }

    /** 경로 앞자리로 화면 이름을 붙인다 — 담당자가 경로를 읽을 필요가 없게. */
    static String menuOf(String path) {
        String p = path.substring(API.length());
        if (p.startsWith("/sales")) {
            return "매출관리";
        }
        if (p.startsWith("/closing") || p.startsWith("/receivables")) {
            return "마감·채권";
        }
        if (p.startsWith("/stock") || p.startsWith("/disposals")) {
            return "재고·수불";
        }
        if (p.startsWith("/consignment")) {
            return "위탁";
        }
        if (p.startsWith("/masters")) {
            return "기초관리";
        }
        if (p.startsWith("/logistics")) {
            return "물류";
        }
        if (p.startsWith("/orders")) {
            return "주문·출고";
        }
        if (p.startsWith("/auth") || p.startsWith("/permissions")) {
            return "인증·권한";
        }
        return null;
    }

    static String username() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || a.getName() == null || "anonymousUser".equals(a.getName())) {
            return "(비로그인)";
        }
        return trim(a.getName(), 50);
    }

    static String role() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || a.getAuthorities() == null) {
            return null;
        }
        return a.getAuthorities().stream().findFirst()
                .map(g -> g.getAuthority().replace("ROLE_", "")).orElse(null);
    }

    /**
     * 쿼리스트링에서 민감한 값을 지운다.
     * ★조회조건은 "무엇을 뽑아 갔나"의 단서라 남길 값이지만, 토큰이 섞여 들어오면
     * 그 자체가 유출 경로가 된다.
     */
    static String safeQuery(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String cleaned = q.replaceAll("(?i)(token|password|secret|authorization)=[^&]*", "$1=***");
        return trim(cleaned, 500);
    }

    /** {@code filename*=UTF-8''...} 우선. 없으면 {@code filename="..."}. */
    private static final java.util.regex.Pattern FILENAME_EXT =
            java.util.regex.Pattern.compile("filename\\*=UTF-8''([^;\\s]+)", java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);
    private static final java.util.regex.Pattern FILENAME_PLAIN =
            java.util.regex.Pattern.compile("filename=\"?([^\";]+)\"?", java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);
    /** 메일용 인코딩 워드 {@code =?UTF-8?Q?..?=} / {@code =?UTF-8?B?..?=}. */
    private static final java.util.regex.Pattern MIME_WORD =
            java.util.regex.Pattern.compile("=\\?UTF-8\\?([QB])\\?(.+?)\\?=", java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);

    /**
     * 다운로드 파일명 추출.
     *
     * <p>★한글 파일명은 헤더에 인코딩돼 나간다. 여기서 풀지 않으면 담당자 화면에
     * {@code %EA%B1%B0%EB%9E%98...} 나 {@code =?UTF-8?Q?=EA=B1=B0...?=} 가 찍혀
     * <b>무슨 파일을 받아 갔는지 알 수 없다</b> — 이 표를 만든 이유 자체가 없어진다.
     */
    private static String fileNameOf(String disposition) {
        if (disposition == null) {
            return null;
        }
        java.util.regex.Matcher ext = FILENAME_EXT.matcher(disposition);
        if (ext.find()) {
            return trim(percentDecode(ext.group(1)), 200);
        }
        java.util.regex.Matcher plain = FILENAME_PLAIN.matcher(disposition);
        if (!plain.find()) {
            return null;
        }
        String raw = plain.group(1);
        java.util.regex.Matcher word = MIME_WORD.matcher(raw);
        if (word.find()) {
            return trim(decodeMimeWord(word.group(1), word.group(2)), 200);
        }
        return trim(percentDecode(raw), 200);
    }

    private static String percentDecode(String s) {
        try {
            // ‼️URLDecoder는 '+'를 공백으로 바꾼다. 파일명의 '+'는 진짜 '+'라 미리 지켜 둔다.
            return java.net.URLDecoder.decode(s.replace("+", "%2B"), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return s;   // 인코딩이 아니면 원문 그대로
        }
    }

    /** Q(quoted-printable) / B(base64) 인코딩 워드를 푼다. */
    private static String decodeMimeWord(String encoding, String text) {
        try {
            // ★equalsIgnoreCase 대신 두 글자를 직접 본다 — 정규식이 [QB] 한 글자만 잡아 오므로
            //   이것으로 충분하고, 로케일에 따라 대소문자 변환이 달라지는 문제도 없다.
            if ("B".equals(encoding) || "b".equals(encoding)) {
                return new String(java.util.Base64.getDecoder().decode(text),
                        java.nio.charset.StandardCharsets.UTF_8);
            }
            // Q: '_'는 공백, '=XX'는 16진 바이트
            byte[] buf = new byte[text.length()];
            int n = 0;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '_') {
                    buf[n++] = ' ';
                } else if (c == '=' && i + 2 < text.length()) {
                    buf[n++] = (byte) Integer.parseInt(text.substring(i + 1, i + 3), 16);
                    i += 2;
                } else {
                    buf[n++] = (byte) c;
                }
            }
            return new String(buf, 0, n, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return text;
        }
    }

    private static Long sizeOf(HttpServletResponse res) {
        String len = res.getHeader(HttpHeaders.CONTENT_LENGTH);
        try {
            return (len == null) ? null : Long.parseLong(len);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 프록시(Caddy) 뒤라 원격주소는 프록시 IP다. 전달 헤더의 첫 값이 실제 클라이언트다. */
    static String clientIp(HttpServletRequest req) {
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            return trim(fwd.split(",")[0].trim(), 45);
        }
        return trim(req.getRemoteAddr(), 45);
    }

    static String trim(String v, int max) {
        if (v == null) {
            return null;
        }
        return v.length() <= max ? v : v.substring(0, max);
    }
}
