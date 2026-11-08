package com.example.aitask.producer;

import com.example.aitask.config.AiProperties;
import com.example.aitask.config.SchedulerProperties;
import com.example.aitask.error.AppException;
import com.example.aitask.error.ErrorCode;
import com.example.aitask.model.AiTask;
import com.example.aitask.model.GenerateInput;
import com.example.aitask.model.GenerateOptions;
import com.example.aitask.storage.AiTaskRepository;
import com.example.aitask.storage.NewAiTask;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 任务生产者：校验请求、补齐默认值、生成 taskId 并持久化 PENDING Task。
 *
 * <p>该类不调用模型，也不创建 Attempt。Attempt 只在一次真实执行得到结果后记录。</p>
 */
@Service
public class TaskProducer {
    private static final Logger log = LoggerFactory.getLogger(TaskProducer.class);
    private static final String TASK_TYPE = "ai.generate";
    private static final int DEFAULT_MAX_OUTPUT_TOKENS = 2000;

    private final AiTaskRepository repository;
    private final AiProperties aiProperties;
    private final SchedulerProperties schedulerProperties;
    private final ObjectMapper objectMapper;

    public TaskProducer(AiTaskRepository repository,
                        AiProperties aiProperties,
                        SchedulerProperties schedulerProperties,
                        ObjectMapper objectMapper) {
        this.repository = repository;
        this.aiProperties = aiProperties;
        this.schedulerProperties = schedulerProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建一个可被 Runner 抢占的新任务。
     *
     * @param command 已完成 HTTP 格式校验的创建命令
     * @return 持久化后的 PENDING Task
     * @throws AppException provider/model/生成参数不符合 V1 契约时抛出
     */
    public AiTask create(CreateTaskCommand command) {
        validate(command);
        // 入库前统一清理文本，并把缺省 options 固化为明确值，Runner 无需再次猜默认值。
        GenerateInput input = new GenerateInput(
                blankToNull(command.input().systemPrompt()),
                command.input().prompt().trim());
        GenerateOptions requested = command.options() == null
                ? new GenerateOptions(null, null) : command.options();
        GenerateOptions options = new GenerateOptions(
                requested.maxOutputTokens() == null
                        ? DEFAULT_MAX_OUTPUT_TOKENS : requested.maxOutputTokens(),
                requested.temperature());
        Instant now = Instant.now();
        // taskId 是对外稳定标识；数据库自增 id 只用于内部关联与高效排序。
        NewAiTask task = new NewAiTask(
                UUID.randomUUID().toString(), TASK_TYPE,
                command.priority() == null ? 0 : command.priority(),
                toJson(input), toJson(options), command.provider(), command.model(),
                schedulerProperties.hardMaxAttempts(), now);
        AiTask created = repository.insert(task);
        log.info("TASK_CREATED | taskId={} | provider={} | model={} | priority={}",
                created.taskId(), created.provider(), created.model(), created.priority());
        return created;
    }

    private void validate(CreateTaskCommand command) {
        if (!TASK_TYPE.equals(command.type())) {
            throw new AppException(ErrorCode.UNSUPPORTED_TASK_TYPE);
        }
        AiProperties.ProviderProperties provider = aiProperties.providers().get(command.provider());
        if (provider == null || !provider.enabled()) {
            throw new AppException(ErrorCode.UNSUPPORTED_PROVIDER);
        }
        if (!provider.supportedModels().contains(command.model())) {
            throw new AppException(ErrorCode.UNSUPPORTED_MODEL);
        }
        // V1 的 GPT-5.6 不接受 temperature，必须明确拒绝，不能静默丢弃参数。
        if ("openai".equals(command.provider())
                && command.options() != null
                && command.options().temperature() != null) {
            throw new AppException(ErrorCode.UNSUPPORTED_OPTION);
        }
        String prompt = command.input().prompt().trim();
        if (prompt.isEmpty() || prompt.length() > 60000) {
            throw new AppException(ErrorCode.PARAM_ERROR);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new AppException(ErrorCode.INVALID_TASK_PAYLOAD, exception);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

}
