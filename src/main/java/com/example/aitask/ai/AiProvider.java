package com.example.aitask.ai;

import com.example.aitask.model.AiProviderResult;
import com.example.aitask.model.GenerateCommand;

/**
 * 模型厂商适配器的最小稳定接口，屏蔽 Spring AI 各客户端的选项差异。
 */
public interface AiProvider {
    /** 返回请求中使用的厂商标识，例如 {@code openai} 或 {@code deepseek}。 */
    String provider();

    /**
     * 执行一次文本生成。
     *
     * @throws AiProviderException Provider 请求失败或响应无法解释时抛出
     */
    AiProviderResult generate(GenerateCommand command);
}
