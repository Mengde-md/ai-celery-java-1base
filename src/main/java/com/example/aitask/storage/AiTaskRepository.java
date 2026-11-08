package com.example.aitask.storage;

import com.example.aitask.model.AiProviderResult;
import com.example.aitask.model.AiTask;
import com.example.aitask.model.ClaimedTask;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Task 聚合的持久化端口。
 *
 * <p>涉及 Task 与 Attempt 同步变化的方法必须由实现层使用同一事务完成。</p>
 */
public interface AiTaskRepository {
    /** 插入一个初始状态为 PENDING 的任务。 */
    AiTask insert(NewAiTask task);

    /** 按对外 taskId 查询任务。 */
    Optional<AiTask> findByTaskId(String taskId);

    /**
     * 使用数据库条件 UPDATE 最多抢占 limit 条任务，并为本批任务创建同一个随机租约令牌。
     */
    List<ClaimedTask> claimReadyTasks(String runnerId, int limit, Duration leaseDuration);

    /** 原子写入成功 Task 和 SUCCEEDED Attempt；租约失效时返回 false。 */
    boolean completeSuccess(ClaimedTask task, AiProviderResult result);

    /** 原子写入失败/待重试 Task 和 FAILED Attempt；租约失效时返回 false。 */
    boolean completeFailure(ClaimedTask task, ExecutionFailure failure);

    /** 幂等取消任务；不存在时返回 empty。 */
    Optional<AiTask> cancel(String taskId);

    /** 查找一批租约已经过期的 RUNNING 任务。 */
    List<ClaimedTask> findExpired(int limit);

    /** 按旧 lease_token 条件恢复一个过期任务，避免并发恢复互相覆盖。 */
    boolean recoverExpired(ClaimedTask task);
}
