package com.example.aitask.model;

/**
 * 一次 Provider 调用上报的输入/输出 Token；null 表示 Provider 没有提供该数据。
 */
public record TokenUsage(Integer inputTokens, Integer outputTokens) {

    /** 创建“Provider 未报告用量”的值对象。 */
    public static TokenUsage unknown() {
        return new TokenUsage(null, null);
    }
}
