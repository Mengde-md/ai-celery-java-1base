package com.example.aitask.error;

import java.util.Map;
import java.util.function.Supplier;

/** 可在任意业务层调用的快速抛错工具。 */
public final class Errors {
    private Errors() {
    }

    /** 创建统一异常，适合 {@code orElseThrow(Errors.exception(...))}。 */
    public static Supplier<AppException> exception(ErrorCode errorCode) {
        return () -> new AppException(errorCode);
    }

    /** 条件成立时抛出统一异常。 */
    public static void throwIf(boolean condition, ErrorCode errorCode) {
        if (condition) {
            throw new AppException(errorCode);
        }
    }

    /** 携带仅供内部日志使用的上下文抛错。 */
    public static void fail(ErrorCode errorCode, Map<String, ?> context) {
        throw new AppException(errorCode, null, context);
    }
}
