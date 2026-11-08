package com.example.aitask.consumer;

import com.example.aitask.config.SchedulerProperties;
import com.example.aitask.storage.AiTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定期回收租约已过期的 RUNNING 任务，处理进程崩溃或请求永久未回写的情况。
 */
@Component
public class ExpiredTaskRecoveryJob {
    private static final Logger log = LoggerFactory.getLogger(ExpiredTaskRecoveryJob.class);

    private final SchedulerProperties properties;
    private final AiTaskRepository repository;

    public ExpiredTaskRecoveryJob(SchedulerProperties properties, AiTaskRepository repository) {
        this.properties = properties;
        this.repository = repository;
    }

    /**
     * 分批扫描过期租约；每条恢复仍使用 lease_token 条件更新，允许多个实例并发执行。
     */
    @Scheduled(fixedDelayString = "${app.scheduler.recovery-interval:30s}")
    public void recover() {
        if (!properties.enabled()) {
            return;
        }
        int recovered = 0;
        for (var task : repository.findExpired(properties.recoveryBatchSize())) {
            if (repository.recoverExpired(task)) {
                recovered++;
            }
        }
        if (recovered > 0) {
            log.info("Recovered expired AI tasks count={}", recovered);
        }
    }
}
