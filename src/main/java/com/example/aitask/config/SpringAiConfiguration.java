package com.example.aitask.config;

import com.example.aitask.ai.AiProvider;
import com.example.aitask.ai.DeepSeekAiProvider;
import com.example.aitask.ai.OpenAiProvider;
import com.example.aitask.logging.LlmInteractionLogger;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 根据配置显式构造 Spring AI 模型客户端。
 *
 * <p>这里不依赖 Starter 自动创建模型，目的是明确控制 120 秒超时和隐藏重试。
 * Provider 未启用时对应 Bean 不会创建，密钥也不会被业务代码直接读取。</p>
 */
@Configuration(proxyBeanMethods = false)
public class SpringAiConfiguration {

    /** 创建 OpenAI 适配器，并把 SDK 内部重试次数固定为 0。 */
    @Bean
    @ConditionalOnProperty(prefix = "app.ai.providers.openai", name = "enabled", havingValue = "true")
    AiProvider openAiProvider(AiProperties properties, LlmInteractionLogger interactionLogger) {
        AiProperties.ProviderProperties provider = requiredProvider(properties, "openai");
        OpenAiChatOptions defaults = OpenAiChatOptions.builder()
                .baseUrl(provider.baseUrl())
                .apiKey(requiredKey(provider, "openai"))
                .timeout(properties.requestTimeout())
                // 调度层自己决定是否产生下一次 Attempt，SDK 不得暗中重复请求。
                .maxRetries(0)
                .build();
        OpenAiChatModel model = OpenAiChatModel.builder().options(defaults).build();
        return new OpenAiProvider(model, interactionLogger);
    }

    /** 创建 DeepSeek 适配器，并统一使用全局连接/读取超时。 */
    @Bean
    @ConditionalOnProperty(prefix = "app.ai.providers.deepseek", name = "enabled", havingValue = "true")
    AiProvider deepSeekAiProvider(AiProperties properties, LlmInteractionLogger interactionLogger) {
        AiProperties.ProviderProperties provider = requiredProvider(properties, "deepseek");
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.requestTimeout());
        requestFactory.setReadTimeout(properties.requestTimeout());
        RestClient.Builder restClientBuilder = RestClient.builder().requestFactory(requestFactory);
        DeepSeekApi api = DeepSeekApi.builder()
                .baseUrl(provider.baseUrl())
                .apiKey(requiredKey(provider, "deepseek"))
                .restClientBuilder(restClientBuilder)
                .build();
        // 一次 generate 最多对应一次 Provider 请求，防止账单和 Attempt 记录失真。
        RetryTemplate noRetry = new RetryTemplate(RetryPolicy.withMaxRetries(0));
        DeepSeekChatModel model = DeepSeekChatModel.builder()
                .deepSeekApi(api)
                .options(DeepSeekChatOptions.builder().build())
                .retryTemplate(noRetry)
                .build();
        return new DeepSeekAiProvider(model, interactionLogger);
    }

    private static AiProperties.ProviderProperties requiredProvider(
            AiProperties properties, String name) {
        AiProperties.ProviderProperties provider = properties.providers().get(name);
        if (provider == null) {
            throw new IllegalStateException("Missing AI provider configuration: " + name);
        }
        return provider;
    }

    private static String requiredKey(AiProperties.ProviderProperties provider, String name) {
        if (provider.apiKey() == null || provider.apiKey().isBlank()) {
            throw new IllegalStateException("Missing API key for enabled provider: " + name);
        }
        return provider.apiKey();
    }
}
