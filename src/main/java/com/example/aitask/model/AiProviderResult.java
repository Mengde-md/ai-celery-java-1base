package com.example.aitask.model;

/** Provider 成功响应的统一形式，包含实际路由、生成结果和可选用量。 */
public record AiProviderResult(
        String provider,
        String model,
        GenerateResult result,
        TokenUsage usage) {
}
