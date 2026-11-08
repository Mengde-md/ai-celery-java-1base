package com.example.aitask.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * 全局接口请求/响应日志过滤器，相当于 Web 层统一日志装饰器。
 *
 * <p>缓存包装器只复制已经被 MVC 读取或写出的内容，不改变 Controller 参数解析。
 * finally 中必须把响应体复制回真实响应，否则客户端将收到空响应。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class HttpExchangeLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(HttpExchangeLoggingFilter.class);

    private final LoggingProperties properties;

    public HttpExchangeLoggingFilter(LoggingProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return properties.excludedPaths().stream().anyMatch(request.getRequestURI()::startsWith);
    }

    /** 记录方法、URL、请求/响应体、HTTP 状态和总耗时。 */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Spring Framework 7 要求显式提供缓存上限；按 UTF-8 单字符最多 4 字节换算，
        // 防止大请求为了日志缓存而无限占用内存。
        int requestCacheLimit = Math.max(properties.maxBodyChars() * 4, 1024);
        ContentCachingRequestWrapper requestWrapper =
                new ContentCachingRequestWrapper(request, requestCacheLimit);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        long startedNanos = System.nanoTime();
        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            long costMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
            String url = request.getQueryString() == null
                    ? request.getRequestURI()
                    : request.getRequestURI() + "?" + request.getQueryString();
            String requestBody = properties.requestBodyEnabled()
                    ? cachedBody(requestWrapper.getContentAsByteArray(), request.getCharacterEncoding(),
                    properties.maxBodyChars()) : "[disabled]";
            String responseBody = properties.responseBodyEnabled()
                    ? cachedBody(responseWrapper.getContentAsByteArray(), response.getCharacterEncoding(),
                    properties.maxBodyChars()) : "[disabled]";

            log.info("API_REQUEST | method={} | url={} | body={}",
                    request.getMethod(), url, requestBody);
            log.info("API_RESPONSE | method={} | url={} | status={} | costMs={} | body={}",
                    request.getMethod(), url, responseWrapper.getStatus(), costMs, responseBody);

            // ContentCachingResponseWrapper 持有响应副本，结束前必须写回真实 response。
            responseWrapper.copyBodyToResponse();
        }
    }

    private static String cachedBody(byte[] content, String encoding, int maxChars) {
        if (content.length == 0) {
            return "";
        }
        Charset charset;
        try {
            charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
        } catch (RuntimeException ignored) {
            charset = StandardCharsets.UTF_8;
        }
        return LogSanitizer.sanitize(new String(content, charset), maxChars);
    }
}
