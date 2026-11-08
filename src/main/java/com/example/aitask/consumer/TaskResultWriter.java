package com.example.aitask.consumer;

import com.example.aitask.ai.AiProviderException;
import com.example.aitask.config.AiProperties;
import com.example.aitask.model.AiErrorType;
import com.example.aitask.model.AiProviderResult;
import com.example.aitask.model.ClaimedTask;
import com.example.aitask.storage.AiTaskRepository;
import com.example.aitask.storage.ExecutionFailure;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * 将 Provider 结果转换为 Task/Attempt 的原子回写请求。
 */
@Component
public class TaskResultWriter {
    private final AiTaskRepository repository;
    private final long maxResultBytes;

    public TaskResultWriter(AiTaskRepository repository, AiProperties properties) {
        this.repository = repository;
        this.maxResultBytes = properties.maxResultBytes().toBytes();
    }

    /**
     * 回写成功结果。超过大小上限的文本不会截断，而是转成不可重试失败。
     *
     * @return {@code true} 表示租约校验通过且已写入；{@code false} 表示结果已过期并被丢弃
     */
    public boolean success(ClaimedTask task, AiProviderResult result) {
        // LONGTEXT 容量不是业务上限；V1 主动限制 UTF-8 字节数，控制数据库体积。
        int resultBytes = result.result().text().getBytes(StandardCharsets.UTF_8).length;
        if (resultBytes > maxResultBytes) {
            return repository.completeFailure(task, new ExecutionFailure(
                    AiErrorType.RESULT_TOO_LARGE, "RESULT_TOO_LARGE",
                    "Provider result exceeds configured size limit", false, result.usage()));
        }
        return repository.completeSuccess(task, result);
    }

    /**
     * 回写规范化失败；是否回到 PENDING 由 Repository 根据 retryable 和尝试次数决定。
     */
    public boolean failure(ClaimedTask task, AiProviderException exception) {
        return repository.completeFailure(task, new ExecutionFailure(
                exception.type(), exception.providerCode(), exception.getMessage(),
                exception.retryable(), exception.usage()));
    }
}
