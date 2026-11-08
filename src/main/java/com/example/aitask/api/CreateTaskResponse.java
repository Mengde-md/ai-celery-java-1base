package com.example.aitask.api;

import com.example.aitask.model.TaskStatus;
import java.time.Instant;

/** 创建任务成功后返回的最小任务摘要。 */
public record CreateTaskResponse(
        String taskId,
        TaskStatus status,
        String provider,
        String model,
        Instant createdAt) {
}
