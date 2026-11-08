package com.example.aitask.ai;

import com.example.aitask.model.AiErrorType;
import com.example.aitask.model.AiProviderResult;
import com.example.aitask.model.GenerateCommand;
import com.example.aitask.model.GenerateResult;
import com.example.aitask.model.TokenUsage;
import com.example.aitask.error.ErrorCode;
import com.example.aitask.logging.LlmInteractionLogger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.metadata.Usage;

/**
 * OpenAI 与 DeepSeek 的公共 Spring AI 调用模板。
 *
 * <p>子类只负责构造厂商特有的 ChatOptions；消息组装、结果提取和异常归一化
 * 在这里保持一致。</p>
 */
abstract class AbstractSpringAiProvider implements AiProvider {
    private final String provider;
    private final ChatModel chatModel;
    private final LlmInteractionLogger interactionLogger;

    protected AbstractSpringAiProvider(String provider, ChatModel chatModel,
                                       LlmInteractionLogger interactionLogger) {
        this.provider = provider;
        this.chatModel = chatModel;
        this.interactionLogger = interactionLogger;
    }

    @Override
    public String provider() {
        return provider;
    }

    /** 组装 Prompt 并发起一次同步模型调用。 */
    @Override
    public AiProviderResult generate(GenerateCommand command) {
        long startedNanos = System.nanoTime();
        List<Message> messages = new ArrayList<>();
        if (command.input().systemPrompt() != null) {
            messages.add(new SystemMessage(command.input().systemPrompt()));
        }
        messages.add(new UserMessage(command.input().prompt()));
        interactionLogger.request(command);
        try {
            // 客户端在配置层已关闭重试，因此这一行最多产生一次 Provider HTTP 请求。
            ChatResponse response = chatModel.call(new Prompt(messages, options(command)));
            AiProviderResult result = toResult(command, response);
            interactionLogger.response(command, result.result(), startedNanos);
            return result;
        } catch (AiProviderException exception) {
            interactionLogger.failure(command, startedNanos, exception);
            throw exception;
        } catch (RuntimeException exception) {
            AiProviderException mapped = SpringAiExceptionMapper.map(exception);
            interactionLogger.failure(command, startedNanos, mapped);
            throw mapped;
        }
    }

    /** 把统一 GenerateCommand 转换为厂商自己的调用参数。 */
    protected abstract ChatOptions options(GenerateCommand command);

    private AiProviderResult toResult(GenerateCommand command, ChatResponse response) {
        Generation generation = response == null ? null : response.getResult();
        String text = generation == null || generation.getOutput() == null
                ? null : generation.getOutput().getText();
        if (text == null || text.isBlank()) {
            throw new AiProviderException(ErrorCode.LLM_NO_RESPONSE, AiErrorType.INTERNAL_ERROR,
                    "EMPTY_RESPONSE", false, null, null);
        }
        String finishReason = generation.getMetadata() == null
                ? null : generation.getMetadata().getFinishReason();
        // 某些兼容接口不返回 usage；这种情况保留“未知”为 0，而不是让任务失败。
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        TokenUsage tokenUsage = usage == null
                ? TokenUsage.unknown()
                : new TokenUsage(usage.getPromptTokens(), usage.getCompletionTokens());
        return new AiProviderResult(provider, command.model(),
                new GenerateResult(text, finishReason), tokenUsage);
    }

    private static long elapsedMillis(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }
}
