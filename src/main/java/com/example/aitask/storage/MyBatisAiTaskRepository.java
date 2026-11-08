package com.example.aitask.storage;

import com.example.aitask.model.AiErrorType;
import com.example.aitask.model.AiProviderResult;
import com.example.aitask.model.AiTask;
import com.example.aitask.model.AttemptStatus;
import com.example.aitask.model.ClaimedTask;
import com.example.aitask.model.TaskStatus;
import com.example.aitask.model.TokenUsage;
import com.example.aitask.storage.mapper.AiTaskMapper;
import com.example.aitask.storage.row.AiTaskAttemptRow;
import com.example.aitask.storage.row.AiTaskRow;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基于 MyBatis 的 Task 聚合实现。
 *
 * <p>这里是状态机持久化边界：Task 条件更新成功后才在同一事务插入 Attempt；
 * 条件更新影响 0 行说明租约已经丢失，必须丢弃旧 Runner 的执行结果。</p>
 */
@Repository
public class MyBatisAiTaskRepository implements AiTaskRepository {
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1024;

    private final AiTaskMapper mapper;

    public MyBatisAiTaskRepository(AiTaskMapper mapper) {
        this.mapper = mapper;
    }

    /** 初始化 PENDING Task；此时尚未发生模型调用，因此不创建 Attempt。 */
    @Override
    @Transactional
    public AiTask insert(NewAiTask task) {
        AiTaskRow row = new AiTaskRow();
        row.taskId = task.taskId();
        row.taskType = task.taskType();
        row.status = TaskStatus.PENDING;
        row.priority = task.priority();
        row.inputJson = task.inputJson();
        row.optionsJson = task.optionsJson();
        row.provider = task.provider();
        row.model = task.model();
        row.attemptCount = 0;
        row.maxAttempts = task.maxAttempts();
        row.inputTokens = 0L;
        row.outputTokens = 0L;
        row.createdAt = task.createdAt();
        row.updatedAt = task.createdAt();
        mapper.insertTask(row);
        return mapper.selectByTaskId(task.taskId()).toDomain();
    }

    @Override
    public Optional<AiTask> findByTaskId(String taskId) {
        return Optional.ofNullable(mapper.selectByTaskId(taskId)).map(AiTaskRow::toDomain);
    }

    /**
     * 用一次条件 UPDATE 完成批量抢占，再通过本批唯一 claimToken 读回准确结果集。
     */
    @Override
    @Transactional
    public List<ClaimedTask> claimReadyTasks(String runnerId, int limit, Duration leaseDuration) {
        if (limit <= 0) {
            return List.of();
        }
        // 每批唯一 token 既用于读回本批任务，也是后续结果回写的所有权凭证。
        String claimToken = UUID.randomUUID().toString();
        Instant leaseExpireAt = Instant.now().plus(leaseDuration);
        // SQL 内含 status=PENDING 条件，多个 Runner 同时更新时只有一个能抢到同一行。
        int claimed = mapper.claimTasks(runnerId, claimToken, leaseExpireAt, limit);
        if (claimed == 0) {
            return List.of();
        }
        return mapper.selectClaimed(runnerId, claimToken).stream()
                .map(row -> new ClaimedTask(row.toDomain(), row.updatedAt))
                .toList();
    }

    /** 成功状态更新和 Attempt 插入处于同一事务，避免只更新一张表。 */
    @Override
    @Transactional
    public boolean completeSuccess(ClaimedTask claimedTask, AiProviderResult result) {
        TokenUsage usage = result.usage() == null ? TokenUsage.unknown() : result.usage();
        AiTask task = claimedTask.task();
        int updated = mapper.completeSuccess(task.id(), task.leaseToken(),
                result.result().text(), result.result().finishReason(),
                usage.inputTokens(), usage.outputTokens());
        if (updated == 0) {
            // 任务可能已取消、租约过期或被恢复；旧结果不能再写入，也不能插 Attempt。
            return false;
        }
        mapper.insertAttempt(attemptRow(claimedTask, AttemptStatus.SUCCEEDED, null,
                null, null, null, usage));
        return true;
    }

    /** 失败时同时判断错误可重试性和剩余尝试次数。 */
    @Override
    @Transactional
    public boolean completeFailure(ClaimedTask claimedTask, ExecutionFailure failure) {
        AiTask task = claimedTask.task();
        // 两个条件缺一不可：错误允许重试，且本次还没有耗尽最大 Attempt 数。
        boolean retry = failure.retryable() && task.attemptCount() < task.maxAttempts();
        TaskStatus targetStatus = retry ? TaskStatus.PENDING : TaskStatus.FAILED;
        TokenUsage usage = failure.usage() == null ? TokenUsage.unknown() : failure.usage();
        int updated = mapper.completeFailure(task.id(), task.leaseToken(), targetStatus,
                !retry, failure.type(), failure.code(), truncate(failure.message()),
                usage.inputTokens(), usage.outputTokens());
        if (updated == 0) {
            // 条件回写失败时不能插入 Attempt，否则记录会声称一个无效结果已生效。
            return false;
        }
        mapper.insertAttempt(attemptRow(claimedTask, AttemptStatus.FAILED,
                failure.retryable(), failure.type(), failure.code(),
                truncate(failure.message()), usage));
        return true;
    }

    /**
     * 在行锁内读取并取消，保证终态幂等；RUNNING 取消仍匹配当时的 lease_token。
     */
    @Override
    @Transactional
    public Optional<AiTask> cancel(String taskId) {
        AiTaskRow row = mapper.selectByTaskIdForUpdate(taskId);
        if (row == null) {
            return Optional.empty();
        }
        if (row.status.terminal()) {
            return Optional.of(row.toDomain());
        }
        if (row.status == TaskStatus.PENDING) {
            mapper.cancelPending(row.id);
        } else if (row.status == TaskStatus.RUNNING) {
            int updated = mapper.cancelRunning(row.id, row.leaseToken);
            if (updated == 1) {
                ClaimedTask claimed = new ClaimedTask(row.toDomain(), row.updatedAt);
                mapper.insertAttempt(attemptRow(claimed, AttemptStatus.FAILED, false,
                        AiErrorType.INTERNAL_ERROR, "TASK_CANCELLED", "Task cancelled",
                        TokenUsage.unknown()));
            }
        }
        return Optional.of(mapper.selectByTaskId(taskId).toDomain());
    }

    @Override
    public List<ClaimedTask> findExpired(int limit) {
        return mapper.selectExpired(limit).stream()
                .map(row -> new ClaimedTask(row.toDomain(), row.updatedAt))
                .toList();
    }

    /** 恢复过期租约；仍有次数则回 PENDING，否则进入 FAILED。 */
    @Override
    @Transactional
    public boolean recoverExpired(ClaimedTask claimedTask) {
        AiTask task = claimedTask.task();
        boolean retry = task.attemptCount() < task.maxAttempts();
        TaskStatus target = retry ? TaskStatus.PENDING : TaskStatus.FAILED;
        int updated = mapper.recoverExpired(task.id(), task.leaseToken(), target, !retry);
        if (updated == 0) {
            return false;
        }
        mapper.insertAttempt(attemptRow(claimedTask, AttemptStatus.FAILED, retry,
                AiErrorType.TIMEOUT, "LEASE_EXPIRED", "Execution lease expired",
                TokenUsage.unknown()));
        return true;
    }

    private static AiTaskAttemptRow attemptRow(
            ClaimedTask claimedTask,
            AttemptStatus status,
            Boolean retryable,
            AiErrorType errorType,
            String errorCode,
            String errorMessage,
            TokenUsage usage) {
        Instant finishedAt = Instant.now();
        AiTask task = claimedTask.task();
        AiTaskAttemptRow row = new AiTaskAttemptRow();
        row.aiTaskId = task.id();
        // attempt_count 在抢占时已经 +1，因此它就是当前执行的 attempt_no。
        row.attemptNo = task.attemptCount();
        row.leaseToken = task.leaseToken();
        row.provider = task.provider();
        row.model = task.model();
        row.status = status;
        row.retryable = retryable;
        row.errorType = errorType;
        row.errorCode = errorCode;
        row.errorMessage = errorMessage;
        row.inputTokens = usage.inputTokens();
        row.outputTokens = usage.outputTokens();
        row.startedAt = claimedTask.attemptStartedAt();
        row.finishedAt = finishedAt;
        // 数据库时间与 JVM 时间存在轻微偏差时，用 0 兜底避免负耗时。
        row.durationMs = Math.max(0, Duration.between(row.startedAt, finishedAt).toMillis());
        return row;
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
