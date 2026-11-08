package com.example.aitask.storage;

import com.example.aitask.model.AiErrorType;
import com.example.aitask.model.TokenUsage;

/** 一次执行失败的持久化信息，已与具体 Spring AI 异常类型解耦。 */
public record ExecutionFailure(
        AiErrorType type,
        String code,
        String message,
        boolean retryable,
        TokenUsage usage) {
}
