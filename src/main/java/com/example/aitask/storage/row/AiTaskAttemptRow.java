package com.example.aitask.storage.row;

import com.example.aitask.model.AiErrorType;
import com.example.aitask.model.AttemptStatus;
import java.time.Instant;

/**
 * MyBatis 使用的 {@code ai_task_attempt} 行对象。
 *
 * <p>Attempt 是不可变的执行结果记录，不承担抢占锁或当前状态判断。</p>
 */
public class AiTaskAttemptRow {
    // 所属任务、本次编号以及证明本次执行身份的租约令牌。
    public Long id;
    public Long aiTaskId;
    public Integer attemptNo;
    public String leaseToken;
    // 实际调用的厂商、模型和执行结论。
    public String provider;
    public String model;
    public AttemptStatus status;
    public Boolean retryable;
    // 失败信息和本次调用上报的 Token 用量。
    public AiErrorType errorType;
    public String errorCode;
    public String errorMessage;
    public Integer inputTokens;
    public Integer outputTokens;
    // 本次执行的时间范围和派生耗时。
    public Instant startedAt;
    public Instant finishedAt;
    public Long durationMs;
}
