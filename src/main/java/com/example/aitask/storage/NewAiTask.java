package com.example.aitask.storage;

import java.time.Instant;

/**
 * 创建任务时写入存储层的数据；状态、尝试次数和 Token 初始值由 Repository 统一设置。
 */
public record NewAiTask(
        String taskId,
        String taskType,
        int priority,
        String inputJson,
        String optionsJson,
        String provider,
        String model,
        int maxAttempts,
        Instant createdAt) {
}
