package com.example.aitask.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.aitask.config.AiProperties;
import com.example.aitask.model.AiProviderResult;
import com.example.aitask.model.ClaimedTask;
import com.example.aitask.model.GenerateResult;
import com.example.aitask.model.TokenUsage;
import com.example.aitask.storage.AiTaskRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

/** TaskResultWriter 的结果大小边界测试。 */
class TaskResultWriterTest {

    /** 超过 UTF-8 字节上限的结果必须按失败回写，不能截断后伪装成功。 */
    @Test
    void turnsOversizedResultIntoFailure() {
        AiTaskRepository repository = mock(AiTaskRepository.class);
        when(repository.completeFailure(any(), any())).thenReturn(true);
        AiProperties properties = new AiProperties(Duration.ofSeconds(120), DataSize.ofBytes(3), Map.of());
        TaskResultWriter writer = new TaskResultWriter(repository, properties);
        ClaimedTask task = new ClaimedTask(null, Instant.now());
        AiProviderResult result = new AiProviderResult("openai", "gpt-5.6",
                new GenerateResult("four", "STOP"), new TokenUsage(1, 1));

        writer.success(task, result);

        verify(repository).completeFailure(any(), any());
    }
}
