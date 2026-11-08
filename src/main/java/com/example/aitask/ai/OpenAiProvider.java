package com.example.aitask.ai;

import com.example.aitask.model.GenerateCommand;
import com.example.aitask.logging.LlmInteractionLogger;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;

/** OpenAI 的 Spring AI 参数适配器，V1 对应用户明确选择的 GPT-5.6。 */
public final class OpenAiProvider extends AbstractSpringAiProvider {

    public OpenAiProvider(ChatModel chatModel, LlmInteractionLogger interactionLogger) {
        super("openai", chatModel, interactionLogger);
    }

    /**
     * 构造 OpenAI 参数。这里故意不设置 temperature；生产者已对该参数明确报错。
     */
    @Override
    protected ChatOptions options(GenerateCommand command) {
        return OpenAiChatOptions.builder()
                .model(command.model())
                .maxCompletionTokens(command.options().maxOutputTokens())
                .build();
    }
}
