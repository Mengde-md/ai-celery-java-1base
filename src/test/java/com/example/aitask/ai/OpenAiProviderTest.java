package com.example.aitask.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.aitask.model.GenerateCommand;
import com.example.aitask.model.GenerateInput;
import com.example.aitask.model.GenerateOptions;
import com.example.aitask.logging.LlmInteractionLogger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

/** OpenAI 参数映射测试，模型调用由 Mock ChatModel 代替。 */
class OpenAiProviderTest {

    /** 确认模型和输出上限正确下传，同时绝不向 GPT-5.6 发送 temperature。 */
    @Test
    void sendsModelAndCompletionLimitWithoutTemperature() {
        ChatModel chatModel = mock(ChatModel.class);
        ChatResponse response = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        AssistantMessage message = mock(AssistantMessage.class);
        when(message.getText()).thenReturn("answer");
        when(generation.getOutput()).thenReturn(message);
        when(response.getResult()).thenReturn(generation);
        when(chatModel.call(any(Prompt.class))).thenReturn(response);

        OpenAiProvider provider = new OpenAiProvider(
                chatModel, mock(LlmInteractionLogger.class));
        provider.generate(new GenerateCommand("task", "openai", "gpt-5.6",
                new GenerateInput("system", "prompt"), new GenerateOptions(123, null)));

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        OpenAiChatOptions options = (OpenAiChatOptions) captor.getValue().getOptions();
        assertThat(options.getModel()).isEqualTo("gpt-5.6");
        assertThat(options.getMaxCompletionTokens()).isEqualTo(123);
        assertThat(options.getTemperature()).isNull();
    }
}
