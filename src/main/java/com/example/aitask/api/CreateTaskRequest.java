package com.example.aitask.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建 AI 异步任务的 HTTP 请求。
 *
 * <p>这里只做通用格式和数值范围校验；Provider 与 model 是否匹配、GPT-5.6
 * 是否支持 temperature 等业务规则由 {@code TaskProducer} 校验。</p>
 */
public record CreateTaskRequest(
        @NotBlank String type,
        @Min(0) @Max(100) Integer priority,
        @NotBlank String provider,
        @NotBlank String model,
        @NotNull @Valid Input input,
        @Valid Options options) {

    /** 模型的系统提示词和用户提示词。 */
    public record Input(
            @Size(max = 10000) String systemPrompt,
            @NotBlank @Size(max = 60000) String prompt) {
    }

    /** V1 允许调用方覆盖的生成参数，不包含 provider 和 model。 */
    public record Options(
            @Min(1) @Max(8000) Integer maxOutputTokens,
            @DecimalMin("0.0") @DecimalMax("2.0") Double temperature) {
    }
}
