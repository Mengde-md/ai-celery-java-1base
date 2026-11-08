package com.example.aitask.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 项目统一业务异常。
 *
 * <p>errorCode 决定对外响应；context 只用于内部排查，不会序列化给调用方。</p>
 */
public class AppException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Map<String, Object> context;

    public AppException(ErrorCode errorCode) {
        this(errorCode, null, Map.of());
    }

    public AppException(ErrorCode errorCode, Throwable cause) {
        this(errorCode, cause, Map.of());
    }

    public AppException(ErrorCode errorCode, Throwable cause, Map<String, ?> context) {
        super(errorCode.msg(), cause);
        this.errorCode = errorCode;
        this.context = immutableContext(context);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, Object> context() {
        return context;
    }

    private static Map<String, Object> immutableContext(Map<String, ?> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
