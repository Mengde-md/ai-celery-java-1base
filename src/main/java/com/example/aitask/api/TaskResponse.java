package com.example.aitask.api;

import com.example.aitask.model.AiErrorType;
import com.example.aitask.model.TaskStatus;
import java.time.Instant;

/**
 * 查询任务的完整响应视图。
 *
 * <p>{@code result} 仅在成功时存在，{@code error} 表示最近一次执行错误；usage
 * 是各次已上报用量的累计值，totalTokens 在返回时计算而不落库。</p>
 */
public record TaskResponse(
        String taskId,
        String type,
        TaskStatus status,
        int priority,
        String provider,
        String model,
        int attemptCount,
        int maxAttempts,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        Result result,
        Error error,
        Usage usage) {

    /** Provider 返回的文本和结束原因。 */
    public record Result(String text, String finishReason) {
    }

    /** 对外暴露的规范化错误，不包含敏感的 Provider 原始响应。 */
    public record Error(AiErrorType type, String code, String message) {
    }

    /** Token 累计值；totalTokens = inputTokens + outputTokens。 */
    public record Usage(long inputTokens, long outputTokens, long totalTokens) {
    }
}
