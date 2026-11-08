package com.example.aitask.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.scheduler} 调度参数。
 *
 * <p>批量抢占数量和最大并发是两个不同概念：前者决定一次最多抢多少条，后者由
 * Semaphore 决定本实例同时允许多少个 Provider 调用。</p>
 */
@ConfigurationProperties("app.scheduler")
public record SchedulerProperties(
        boolean enabled,
        Duration minPollInterval,
        Duration maxPollInterval,
        int claimBatchSize,
        int maxConcurrency,
        Duration leaseDuration,
        Duration recoveryInterval,
        int recoveryBatchSize,
        int hardMaxAttempts) {

    public SchedulerProperties {
        // 所有默认值都与 V1 设计文档一致，非法的非正数回退到保守值。
        minPollInterval = minPollInterval == null ? Duration.ofMillis(800) : minPollInterval;
        maxPollInterval = maxPollInterval == null ? Duration.ofMillis(1200) : maxPollInterval;
        leaseDuration = leaseDuration == null ? Duration.ofMinutes(4) : leaseDuration;
        recoveryInterval = recoveryInterval == null ? Duration.ofSeconds(30) : recoveryInterval;
        claimBatchSize = claimBatchSize <= 0 ? 10 : claimBatchSize;
        maxConcurrency = maxConcurrency <= 0 ? 10 : maxConcurrency;
        recoveryBatchSize = recoveryBatchSize <= 0 ? 100 : recoveryBatchSize;
        // V1 的硬上限为 3，配置不能绕过该边界无限重试。
        hardMaxAttempts = hardMaxAttempts <= 0 ? 3 : Math.min(hardMaxAttempts, 3);
        if (minPollInterval.compareTo(maxPollInterval) > 0) {
            throw new IllegalArgumentException("min-poll-interval must not exceed max-poll-interval");
        }
    }
}
