package com.example.aitask.logging;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** HTTP 与 LLM 日志内容控制项；滚动策略由 logback-spring.xml 配置。 */
@ConfigurationProperties("app.logging")
public record LoggingProperties(
        boolean requestBodyEnabled,
        boolean responseBodyEnabled,
        int maxBodyChars,
        int maxPromptChars,
        int maxResponseChars,
        List<String> excludedPaths) {

    public LoggingProperties {
        maxBodyChars = maxBodyChars <= 0 ? 8_000 : maxBodyChars;
        maxPromptChars = maxPromptChars <= 0 ? 16_000 : maxPromptChars;
        maxResponseChars = maxResponseChars <= 0 ? 16_000 : maxResponseChars;
        excludedPaths = excludedPaths == null ? List.of() : List.copyOf(excludedPaths);
    }
}
