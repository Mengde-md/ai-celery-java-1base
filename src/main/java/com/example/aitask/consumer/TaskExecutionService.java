package com.example.aitask.consumer;

import com.example.aitask.ai.AiProvider;
import com.example.aitask.ai.AiProviderException;
import com.example.aitask.ai.AiProviderSelector;
import com.example.aitask.error.ErrorCode;
import com.example.aitask.model.AiErrorType;
import com.example.aitask.model.AiProviderResult;
import com.example.aitask.model.ClaimedTask;
import com.example.aitask.model.GenerateCommand;
import com.example.aitask.model.GenerateInput;
import com.example.aitask.model.GenerateOptions;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 把已抢占的数据库任务还原为生成命令，并精确路由到指定 AI Provider。
 */
@Service
public class TaskExecutionService {
    private final AiProviderSelector selector;
    private final ObjectMapper objectMapper;

    public TaskExecutionService(AiProviderSelector selector, ObjectMapper objectMapper) {
        this.selector = selector;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行一次模型生成。Provider 客户端已关闭隐藏重试，因此最多产生一次 HTTP 请求。
     */
    public AiProviderResult execute(ClaimedTask claimedTask) {
        try {
            var task = claimedTask.task();
            // input/options 在生产阶段以 JSON 固化，执行阶段不重新套用默认值。
            GenerateInput input = objectMapper.readValue(task.inputJson(), GenerateInput.class);
            GenerateOptions options = objectMapper.readValue(task.optionsJson(), GenerateOptions.class);
            GenerateCommand command = new GenerateCommand(
                    task.taskId(), task.provider(), task.model(), input, options);
            // 只按用户已选 provider 路由，不做自动降级或跨厂商切换。
            AiProvider provider = selector.select(task.provider());
            return provider.generate(command);
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new AiProviderException(ErrorCode.INVALID_TASK_PAYLOAD,
                    AiErrorType.INTERNAL_ERROR, "INVALID_TASK_PAYLOAD",
                    false, null, exception);
        }
    }
}
