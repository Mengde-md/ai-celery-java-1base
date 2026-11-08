package com.example.aitask.storage.mapper;

import com.example.aitask.model.AiErrorType;
import com.example.aitask.model.TaskStatus;
import com.example.aitask.storage.row.AiTaskAttemptRow;
import com.example.aitask.storage.row.AiTaskRow;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * Task/Attempt SQL 映射接口，具体 SQL 位于 {@code mapper/AiTaskMapper.xml}。
 */
public interface AiTaskMapper {
    int insertTask(AiTaskRow row);

    AiTaskRow selectByTaskId(@Param("taskId") String taskId);

    /** 带行锁读取取消目标，使“检查状态并取消”保持原子。 */
    AiTaskRow selectByTaskIdForUpdate(@Param("taskId") String taskId);

    /**
     * 条件批量抢占。返回实际更新行数；调用前不需要先 SELECT 候选任务。
     */
    int claimTasks(@Param("runnerId") String runnerId,
                   @Param("claimToken") String claimToken,
                   @Param("leaseExpireAt") Instant leaseExpireAt,
                   @Param("limit") int limit);

    /** 按本批唯一 token 读回刚刚被当前 Runner 抢占的任务。 */
    List<AiTaskRow> selectClaimed(@Param("runnerId") String runnerId,
                                  @Param("claimToken") String claimToken);

    /** 带有效租约条件回写成功，返回 0 表示结果已失去写入资格。 */
    int completeSuccess(@Param("id") long id,
                        @Param("leaseToken") String leaseToken,
                        @Param("resultText") String resultText,
                        @Param("finishReason") String finishReason,
                        @Param("inputTokens") Integer inputTokens,
                        @Param("outputTokens") Integer outputTokens);

    /** 带有效租约条件回写失败或 PENDING 重试状态。 */
    int completeFailure(@Param("id") long id,
                        @Param("leaseToken") String leaseToken,
                        @Param("targetStatus") TaskStatus targetStatus,
                        @Param("terminal") boolean terminal,
                        @Param("errorType") AiErrorType errorType,
                        @Param("errorCode") String errorCode,
                        @Param("errorMessage") String errorMessage,
                        @Param("inputTokens") Integer inputTokens,
                        @Param("outputTokens") Integer outputTokens);

    int cancelPending(@Param("id") long id);

    int cancelRunning(@Param("id") long id, @Param("leaseToken") String leaseToken);

    /** Attempt 仅在一次执行得到结论后插入。 */
    int insertAttempt(AiTaskAttemptRow row);

    List<AiTaskRow> selectExpired(@Param("limit") int limit);

    /** 使用原 lease_token 条件恢复过期任务，防止并发恢复覆盖新租约。 */
    int recoverExpired(@Param("id") long id,
                       @Param("leaseToken") String leaseToken,
                       @Param("targetStatus") TaskStatus targetStatus,
                       @Param("terminal") boolean terminal);
}
