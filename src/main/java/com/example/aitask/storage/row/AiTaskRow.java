package com.example.aitask.storage.row;

import com.example.aitask.model.AiErrorType;
import com.example.aitask.model.AiTask;
import com.example.aitask.model.TaskStatus;
import java.time.Instant;

/**
 * MyBatis 使用的可变数据库行对象，字段与 {@code ai_task} 列一一对应。
 *
 * <p>它只存在于 storage 层；其他模块使用不可变的 {@link AiTask}。</p>
 */
public class AiTaskRow {
    // 身份、调度状态和序列化请求。
    public Long id;
    public String taskId;
    public String taskType;
    public TaskStatus status;
    public Integer priority;
    public String inputJson;
    public String optionsJson;
    // 模型结果与路由信息。
    public String resultText;
    public String finishReason;
    public String provider;
    public String model;
    // Attempt 计数与租约所有权。
    public Integer attemptCount;
    public Integer maxAttempts;
    public String leaseOwner;
    public String leaseToken;
    public Instant leaseExpireAt;
    // 最近错误、累计用量和生命周期时间。
    public AiErrorType lastErrorType;
    public String lastErrorCode;
    public String lastErrorMessage;
    public Long inputTokens;
    public Long outputTokens;
    public Instant startedAt;
    public Instant finishedAt;
    public Instant createdAt;
    public Instant updatedAt;

    /** 转换为跨层传递的不可变领域快照。 */
    public AiTask toDomain() {
        return new AiTask(id, taskId, taskType, status, priority, inputJson, optionsJson,
                resultText, finishReason, provider, model, attemptCount, maxAttempts,
                leaseOwner, leaseToken, leaseExpireAt, lastErrorType, lastErrorCode,
                lastErrorMessage, inputTokens, outputTokens, startedAt, finishedAt,
                createdAt, updatedAt);
    }
}
