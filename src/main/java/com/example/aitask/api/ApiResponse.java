package com.example.aitask.api;

import com.example.aitask.error.ErrorCode;

/**
 * 业务接口统一响应外壳。
 *
 * <p>业务接口固定返回 HTTP 200，数字 {@code code} 表达稳定的业务结果；成功固定为
 * 0，失败时 {@code data} 固定为 {@code null}。健康检查等运维端点不受此约定限制。</p>
 */
public record ApiResponse<T>(int code, String msg, T data) {

    /** 创建成功响应。 */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.code(), ErrorCode.SUCCESS.msg(), data);
    }

    /** 创建不携带业务数据的错误响应。 */
    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.code(), errorCode.msg(), null);
    }
}
