package com.example.aitask.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 为每个 HTTP 请求维护 Request ID，并写入响应头和日志 MDC。
 *
 * <p>调用方传入格式有效的 ID 时沿用，否则由服务端生成。finally 中必须清理 MDC，
 * 否则线程复用时可能把上一个请求的标识带到下一条日志。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Request-Id";
    private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    /** 为单次请求建立并在结束后清理 Request ID 上下文。 */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(HEADER);
        if (requestId == null || !REQUEST_ID.matcher(requestId).matches()) {
            requestId = UUID.randomUUID().toString();
        }
        response.setHeader(HEADER, requestId);
        // MDC 使本次请求内的所有日志都能自动带上 requestId。
        MDC.put("requestId", requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 必须清理，防止容器线程复用造成日志串号。
            MDC.remove("requestId");
        }
    }
}
