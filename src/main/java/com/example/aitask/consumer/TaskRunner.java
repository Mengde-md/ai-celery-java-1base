package com.example.aitask.consumer;

import com.example.aitask.ai.AiProviderException;
import com.example.aitask.error.ErrorCode;
import com.example.aitask.model.ClaimedTask;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * 单个已抢占任务的执行编排器，负责调用、错误归一化、结果回写和日志上下文。
 */
@Component
public class TaskRunner {
    private static final Logger log = LoggerFactory.getLogger(TaskRunner.class);

    private final TaskExecutionService executionService;
    private final TaskResultWriter resultWriter;
    private final RunnerIdentity runnerIdentity;

    public TaskRunner(TaskExecutionService executionService,
                      TaskResultWriter resultWriter,
                      RunnerIdentity runnerIdentity) {
        this.executionService = executionService;
        this.resultWriter = resultWriter;
        this.runnerIdentity = runnerIdentity;
    }

    /**
     * 执行一个 Attempt。即使租约在调用期间失效，也不会覆盖新 Runner 的结果，
     * 因为最终回写仍需由 Repository 校验 status、lease_token 和 lease_expire_at。
     */
    public void run(ClaimedTask task) {
        long startNanos = System.nanoTime();
        MDC.put("taskId", task.task().taskId());
        MDC.put("attemptNo", Integer.toString(task.task().attemptCount()));
        try {
            boolean written = resultWriter.success(task, executionService.execute(task));
            log.info("AI task completed provider={} model={} leaseOwner={} persisted={} durationMs={}",
                    task.task().provider(), task.task().model(), runnerIdentity.runnerId(), written,
                    elapsedMillis(startNanos));
        } catch (AiProviderException exception) {
            boolean written = resultWriter.failure(task, exception);
            log.warn("AI task failed provider={} model={} leaseOwner={} errorType={} persisted={} durationMs={}",
                    task.task().provider(), task.task().model(), runnerIdentity.runnerId(),
                    exception.type(), written, elapsedMillis(startNanos));
        } catch (RuntimeException exception) {
            // 非 Provider 预期异常不可重试，避免代码缺陷形成高速重试循环。
            AiProviderException mapped = new AiProviderException(
                    ErrorCode.SERVER_ERROR,
                    com.example.aitask.model.AiErrorType.INTERNAL_ERROR,
                    "RUNNER_ERROR", false, null, exception);
            boolean written = resultWriter.failure(task, mapped);
            log.error("AI task runner failed provider={} model={} leaseOwner={} persisted={} durationMs={}",
                    task.task().provider(), task.task().model(), runnerIdentity.runnerId(), written,
                    elapsedMillis(startNanos), exception);
        } finally {
            // 虚拟线程执行结束后仍显式清理 MDC，保持日志上下文边界清晰。
            MDC.remove("taskId");
            MDC.remove("attemptNo");
        }
    }

    private static long elapsedMillis(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }
}
