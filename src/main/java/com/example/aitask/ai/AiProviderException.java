package com.example.aitask.ai;

import com.example.aitask.error.AppException;
import com.example.aitask.error.ErrorCode;
import com.example.aitask.model.AiErrorType;
import com.example.aitask.model.TokenUsage;

/**
 * Provider 调用错误的统一形式。
 *
 * <p>{@code retryable} 只是本次错误的分类；Repository 还会结合 attempt_count 与
 * max_attempts 决定任务回到 PENDING 还是进入 FAILED。</p>
 */
public class AiProviderException extends AppException {
    private final AiErrorType type;
    private final String providerCode;
    private final boolean retryable;
    private final TokenUsage usage;

    public AiProviderException(ErrorCode errorCode, AiErrorType type, String providerCode,
                               boolean retryable, TokenUsage usage, Throwable cause) {
        super(errorCode, cause);
        this.type = type;
        this.providerCode = providerCode;
        this.retryable = retryable;
        this.usage = usage;
    }

    /** 返回面向任务系统的错误大类。 */
    public AiErrorType type() {
        return type;
    }

    /** 返回稳定的 Provider/HTTP 错误码。 */
    public String providerCode() {
        return providerCode;
    }

    /** 返回该错误在语义上是否允许重试。 */
    public boolean retryable() {
        return retryable;
    }

    /** 返回失败响应中仍可能存在的 Token 用量。 */
    public TokenUsage usage() {
        return usage;
    }
}
