package com.example.aitask.web;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/** Server-side site entry code, evaluated before request-body logging. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class SiteAccessFilter extends OncePerRequestFilter {
    static final String COOKIE_NAME = "cd_site_access";
    private static final String ACCESS_PATH = "/api/site-access";
    private static final Duration VALIDITY = Duration.ofHours(8);
    private final String accessCode;
    private final ObjectMapper mapper;
    private final byte[] signingKey = new byte[32];

    public SiteAccessFilter(@Value("${app.access-code}") String accessCode,
                            ObjectMapper mapper) {
        this.accessCode = accessCode == null ? "" : accessCode;
        this.mapper = mapper;
        new SecureRandom().nextBytes(signingKey);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = path(request);
        return !path.equals("/api") && !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        boolean required = !accessCode.isBlank();
        if (ACCESS_PATH.equals(path(request))) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            if ("GET".equals(request.getMethod())) {
                json(response, 200, Map.of("required", required,
                        "granted", !required || hasAccess(request)));
            } else if ("POST".equals(request.getMethod())) {
                verifyCode(request, response, required);
            } else {
                response.setHeader(HttpHeaders.ALLOW, "GET, POST");
                json(response, 405, Map.of("message", "请求方式不支持"));
            }
            return;
        }
        if (required && !"OPTIONS".equals(request.getMethod()) && !hasAccess(request)) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            json(response, 401, Map.of("code", "SITE_ACCESS_REQUIRED", "message", "请输入访问码"));
            return;
        }
        chain.doFilter(request, response);
    }

    private void verifyCode(HttpServletRequest request, HttpServletResponse response,
                            boolean required) throws IOException {
        byte[] body = request.getInputStream().readNBytes(1025);
        if (body.length > 1024) {
            json(response, 400, Map.of("message", "请输入访问码"));
            return;
        }
        String supplied;
        try {
            Map<?, ?> input = mapper.readValue(body, Map.class);
            Object value = input == null ? null : input.get("code");
            supplied = value instanceof String text ? text : "";
        } catch (JacksonException invalidJson) {
            json(response, 400, Map.of("message", "请输入访问码"));
            return;
        }
        if (required && !same(accessCode, supplied)) {
            json(response, 401, Map.of("message", "访问码不正确"));
            return;
        }
        if (required) {
            String expiry = Long.toString(Instant.now().plus(VALIDITY).getEpochSecond());
            String token = expiry + "." + sign(expiry);
            boolean secure = request.isSecure()
                    || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
            ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, token)
                    .httpOnly(true).secure(secure).sameSite("Lax")
                    .path("/").maxAge(VALIDITY).build();
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        }
        json(response, 200, Map.of("granted", true));
    }

    private boolean hasAccess(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return false;
        for (Cookie cookie : cookies) {
            if (!COOKIE_NAME.equals(cookie.getName())) continue;
            String token = cookie.getValue();
            if (token == null || token.length() > 128) return false;
            int separator = token.indexOf('.');
            if (separator < 1) return false;
            String expiry = token.substring(0, separator);
            try {
                long expiresAt = Long.parseLong(expiry);
                if (expiresAt <= Instant.now().getEpochSecond()) return false;
                return same(sign(expiry), token.substring(separator + 1));
            } catch (NumberFormatException malformed) {
                return false;
            }
        }
        return false;
    }

    private String sign(String expiry) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(expiry.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("Cannot create site access token", unavailable);
        }
    }

    private static boolean same(String expected, String supplied) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }

    private static String path(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        return servletPath.isEmpty()
                ? request.getRequestURI().substring(request.getContextPath().length()) : servletPath;
    }

    private void json(HttpServletResponse response, int status, Map<String, ?> body) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        mapper.writeValue(response.getWriter(), body);
    }
}
