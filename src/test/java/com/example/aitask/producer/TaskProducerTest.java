package com.example.aitask.producer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.aitask.config.AiProperties;
import com.example.aitask.config.SchedulerProperties;
import com.example.aitask.error.AppException;
import com.example.aitask.error.ErrorCode;
import com.example.aitask.model.AiTask;
import com.example.aitask.model.GenerateInput;
import com.example.aitask.model.GenerateOptions;
import com.example.aitask.storage.AiTaskRepository;
import com.example.aitask.storage.NewAiTask;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import org.mockito.ArgumentCaptor;
import org.springframework.util.unit.DataSize;

/** TaskProducer 的业务契约测试，不连接数据库或真实模型。 */
class TaskProducerTest {

    /** 验证缺省优先级、输出上限和输入清理会在持久化前完成。 */
    @Test
    void createsDeepSeekTaskWithDefaults() {
        AiTaskRepository repository = mock(AiTaskRepository.class);
        when(repository.insert(any())).thenAnswer(invocation -> {
            NewAiTask value = invocation.getArgument(0);
            return mockTask(value);
        });
        TaskProducer producer = new TaskProducer(repository, properties(), scheduler(), new ObjectMapper());

        AiTask task = producer.create(new CreateTaskCommand(
                "ai.generate", null, "deepseek", "deepseek-v4-pro",
                new GenerateInput(null, " hello "), null));

        assertThat(task.status().name()).isEqualTo("PENDING");
        ArgumentCaptor<NewAiTask> captor = ArgumentCaptor.forClass(NewAiTask.class);
        verify(repository).insert(captor.capture());
        assertThat(captor.getValue().priority()).isZero();
        assertThat(captor.getValue().inputJson()).contains("hello");
        assertThat(captor.getValue().optionsJson()).contains("2000");
    }

    /** 验证 GPT-5.6 的不支持参数被明确拒绝，而不是静默忽略。 */
    @Test
    void rejectsTemperatureForGpt56() {
        TaskProducer producer = new TaskProducer(mock(AiTaskRepository.class),
                properties(), scheduler(), new ObjectMapper());

        assertThatThrownBy(() -> producer.create(new CreateTaskCommand(
                "ai.generate", 0, "openai", "gpt-5.6",
                new GenerateInput(null, "hello"), new GenerateOptions(100, 0.2))))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).errorCode())
                .isEqualTo(ErrorCode.UNSUPPORTED_OPTION);
    }

    private static AiProperties properties() {
        return new AiProperties(Duration.ofSeconds(120), DataSize.ofMegabytes(1), Map.of(
                "openai", new AiProperties.ProviderProperties(true, "https://api.openai.com", "test", List.of("gpt-5.6")),
                "deepseek", new AiProperties.ProviderProperties(true, "https://api.deepseek.com", "test", List.of("deepseek-v4-pro"))));
    }

    private static SchedulerProperties scheduler() {
        return new SchedulerProperties(true, Duration.ofMillis(800), Duration.ofMillis(1200),
                10, 10, Duration.ofMinutes(4), Duration.ofSeconds(30), 100, 3);
    }

    private static AiTask mockTask(NewAiTask value) {
        return new AiTask(1, value.taskId(), value.taskType(), com.example.aitask.model.TaskStatus.PENDING,
                value.priority(), value.inputJson(), value.optionsJson(), null, null,
                value.provider(), value.model(), 0, value.maxAttempts(), null, null, null,
                null, null, null, 0, 0, null, null, value.createdAt(), value.createdAt());
    }
}
