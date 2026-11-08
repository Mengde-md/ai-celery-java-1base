package com.example.aitask.model;

import java.time.Instant;

/**
 * Task 聚合的不可变快照。
 *
 * <p>taskId 是对外标识，id 是数据库内部主键；attemptCount 在每次成功抢占时增加；
 * leaseOwner/leaseToken/leaseExpireAt 共同描述当前 RUNNING 所有权；Token 字段累计
 * 所有已上报 Attempt 的用量。</p>
 */
public record AiTask(
        long id,
        String taskId,
        String taskType,
        TaskStatus status,
        int priority,
        String inputJson,
        String optionsJson,
        String resultText,
        String finishReason,
        String provider,
        String model,
        int attemptCount,
        int maxAttempts,
        String leaseOwner,
        String leaseToken,
        Instant leaseExpireAt,
        AiErrorType lastErrorType,
        String lastErrorCode,
        String lastErrorMessage,
        long inputTokens,
        long outputTokens,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt,
        Instant updatedAt) {
}
