package com.example.aitask.ai;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 按 Task 中持久化的 provider 精确选择适配器，不做别名、自动降级或厂商切换。
 */
@Component
public class AiProviderSelector {
    private final Map<String, AiProvider> providers;

    public AiProviderSelector(List<AiProvider> providers) {
        // 构造成不可变 Map；重复 provider 会在启动时直接失败，避免路由结果不确定。
        this.providers = providers.stream()
                .collect(Collectors.toUnmodifiableMap(AiProvider::provider, Function.identity()));
    }

    /**
     * 返回已启用的 Provider。
     *
     * @throws IllegalArgumentException Provider 未启用或当前实例没有对应 Bean
     */
    public AiProvider select(String provider) {
        AiProvider selected = providers.get(provider);
        if (selected == null) {
            throw new IllegalArgumentException("No enabled AI provider: " + provider);
        }
        return selected;
    }
}
