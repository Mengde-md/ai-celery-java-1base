package com.example.aitask.ai;

import com.example.aitask.model.GenerateCommand;
import com.example.aitask.logging.LlmInteractionLogger;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.deepseek.DeepSeekChatOptions;

/** DeepSeek 文本生成适配器，支持配置白名单中的 Flash 与 Pro 模型。 */
public final class DeepSeekAiProvider extends AbstractSpringAiProvider {

    public DeepSeekAiProvider(ChatModel chatModel, LlmInteractionLogger interactionLogger) {
        super("deepseek", chatModel, interactionLogger);
    }

    /** 构造 DeepSeek 参数；temperature 只有调用方显式传入时才发送。 */
    @Override
    protected ChatOptions options(GenerateCommand command) {
        var builder = DeepSeekChatOptions.builder()
                .model(command.model())
                // 普通文本任务不消耗输出预算生成推理过程，temperature 也能按请求生效。
                .disableThinking()
                .maxTokens(command.options().maxOutputTokens());
        if (command.options().temperature() != null) {
            builder.temperature(command.options().temperature());
        }
        return builder.build();
    }
}
