package com.example.aitask.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.aitask.error.ErrorCode;
import com.example.aitask.logging.LlmInteractionLogger;
import com.example.aitask.model.GenerateCommand;
import com.example.aitask.model.GenerateInput;
import com.example.aitask.model.GenerateOptions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionRequest.Thinking;

class DeepSeekAiProviderTest {
    @Test
    void sendsOneFlashGenerationWithThinkingDisabled() {
        ChatModel model = modelReturning("模型最终回答");
        DeepSeekAiProvider provider = new DeepSeekAiProvider(model, mock(LlmInteractionLogger.class));

        var result = provider.generate(command("deepseek-flash", 256, 0.4));

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(1)).call(captor.capture());
        DeepSeekChatOptions options = (DeepSeekChatOptions) captor.getValue().getOptions();
        assertThat(options.getModel()).isEqualTo("deepseek-flash");
        assertThat(options.getMaxTokens()).isEqualTo(256);
        assertThat(options.getTemperature()).isEqualTo(0.4);
        assertThat(options.getThinking()).isEqualTo(Thinking.DISABLED);
        assertThat(result.result().text()).isEqualTo("模型最终回答");
    }

    @Test
    void preservesProSelectionAndDoesNotInventTemperature() {
        ChatModel model = modelReturning("回答");
        DeepSeekAiProvider provider = new DeepSeekAiProvider(model, mock(LlmInteractionLogger.class));

        provider.generate(command("deepseek-v4-pro", 512, null));

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(captor.capture());
        DeepSeekChatOptions options = (DeepSeekChatOptions) captor.getValue().getOptions();
        assertThat(options.getModel()).isEqualTo("deepseek-v4-pro");
        assertThat(options.getTemperature()).isNull();
        assertThat(options.getThinking()).isEqualTo(Thinking.DISABLED);
    }

    @Test
    void emptyFinalContentIsNotStoredAsSuccessfulGeneration() {
        DeepSeekAiProvider provider = new DeepSeekAiProvider(
                modelReturning(" \n"), mock(LlmInteractionLogger.class));

        assertThatThrownBy(() -> provider.generate(command("deepseek-flash", 32, null)))
                .isInstanceOf(AiProviderException.class)
                .satisfies(error -> {
                    AiProviderException failure = (AiProviderException) error;
                    assertThat(failure.errorCode()).isEqualTo(ErrorCode.LLM_NO_RESPONSE);
                    assertThat(failure.providerCode()).isEqualTo("EMPTY_RESPONSE");
                    assertThat(failure.retryable()).isFalse();
                });
    }

    private static GenerateCommand command(String model, int limit, Double temperature) {
        return new GenerateCommand("test-task", "deepseek", model,
                new GenerateInput("请给出简洁回答", "你好"), new GenerateOptions(limit, temperature));
    }

    private static ChatModel modelReturning(String text) {
        ChatModel model = mock(ChatModel.class);
        ChatResponse response = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        AssistantMessage message = mock(AssistantMessage.class);
        when(message.getText()).thenReturn(text);
        when(generation.getOutput()).thenReturn(message);
        when(response.getResult()).thenReturn(generation);
        when(model.call(any(Prompt.class))).thenReturn(response);
        return model;
    }
}
