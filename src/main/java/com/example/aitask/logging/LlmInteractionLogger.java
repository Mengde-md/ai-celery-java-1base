package com.example.aitask.logging;

import com.example.aitask.model.GenerateCommand;
import com.example.aitask.model.GenerateResult;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 大模型交互专用日志入口，统一模型、提示词、响应、耗时与异常字段。 */
@Component
public class LlmInteractionLogger {
    private static final Logger log = LoggerFactory.getLogger(LlmInteractionLogger.class);

    private final LoggingProperties properties;

    public LlmInteractionLogger(LoggingProperties properties) {
        this.properties = properties;
    }

    /** 记录即将发送给 Provider 的请求，不记录 API Key。 */
    public void request(GenerateCommand command) {
        log.info("LLM_REQUEST | provider={} | model={} | taskId={} | systemPrompt={} | prompt={}",
                command.provider(), command.model(), command.taskId(),
                properties.requestBodyEnabled()
                        ? LogSanitizer.sanitize(command.input().systemPrompt(), properties.maxPromptChars()) : "[disabled]",
                properties.requestBodyEnabled()
                        ? LogSanitizer.sanitize(command.input().prompt(), properties.maxPromptChars()) : "[disabled]");
    }

    /** 记录成功响应与调用耗时。 */
    public void response(GenerateCommand command, GenerateResult result, long startedNanos) {
        log.info("LLM_RESPONSE | provider={} | model={} | taskId={} | costMs={} | finishReason={} | response={}",
                command.provider(), command.model(), command.taskId(), elapsedMillis(startedNanos),
                result.finishReason(),
                properties.responseBodyEnabled()
                        ? LogSanitizer.sanitize(result.text(), properties.maxResponseChars()) : "[disabled]");
    }

    /** 记录完整异常堆栈；该 ERROR 同时进入主日志和独立 error 文件。 */
    public void failure(GenerateCommand command, long startedNanos, Throwable throwable) {
        log.error("LLM_ERROR | provider={} | model={} | taskId={} | costMs={} | exceptionType={}",
                command.provider(), command.model(), command.taskId(), elapsedMillis(startedNanos),
                throwable.getClass().getName(), throwable);
    }

    private static long elapsedMillis(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }
}
