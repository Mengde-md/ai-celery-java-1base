package com.example.aitask.consumer;

import com.example.aitask.config.SchedulerProperties;
import com.example.aitask.model.ClaimedTask;
import com.example.aitask.storage.AiTaskRepository;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * 消费端轮询器：按随机间隔申请并发槽位、批量抢占任务并提交给 Runner。
 *
 * <p>多个实例不依赖随机时间保证互斥；最终互斥由数据库的条件 UPDATE 提供。
 * 随机间隔只是降低多个实例同时打到数据库的概率。</p>
 */
@Component
public class TaskPoller {
    private static final Logger log = LoggerFactory.getLogger(TaskPoller.class);

    private final SchedulerProperties properties;
    private final AiTaskRepository repository;
    private final RunnerIdentity runnerIdentity;
    private final TaskRunner taskRunner;
    private final TaskScheduler taskScheduler;
    private final ExecutorService taskExecutor;
    private final Semaphore providerPermits;
    private final AtomicBoolean stopped = new AtomicBoolean();

    public TaskPoller(SchedulerProperties properties,
                      AiTaskRepository repository,
                      RunnerIdentity runnerIdentity,
                      TaskRunner taskRunner,
                      TaskScheduler taskScheduler,
                      ExecutorService taskExecutor,
                      Semaphore providerPermits) {
        this.properties = properties;
        this.repository = repository;
        this.runnerIdentity = runnerIdentity;
        this.taskRunner = taskRunner;
        this.taskScheduler = taskScheduler;
        this.taskExecutor = taskExecutor;
        this.providerPermits = providerPermits;
    }

    /** 应用完全启动后才开始轮询，避免初始化阶段提前访问数据库。 */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (properties.enabled()) {
            scheduleNext();
        }
    }

    /** 执行一次“申请槽位 → 抢占 → 异步执行”，结束后安排下一次随机轮询。 */
    void poll() {
        int reserved = 0;
        try {
            // 先占用本地并发槽位，再按槽位数抢任务，避免任务已 RUNNING 却在本机排队。
            reserved = reservePermits();
            if (reserved == 0) {
                return;
            }
            List<ClaimedTask> tasks;
            try {
                // repository 内部用一条条件 UPDATE 完成抢占，不先 SELECT 候选任务。
                tasks = repository.claimReadyTasks(
                        runnerIdentity.runnerId(), reserved, properties.leaseDuration());
            } catch (RuntimeException exception) {
                providerPermits.release(reserved);
                reserved = 0;
                throw exception;
            }
            // 数据库中可抢任务可能少于空闲槽位，立即归还没有实际使用的部分。
            providerPermits.release(reserved - tasks.size());
            reserved = 0;
            for (ClaimedTask task : tasks) {
                try {
                    taskExecutor.submit(() -> {
                        try {
                            taskRunner.run(task);
                        } finally {
                            // 无论执行成功、失败还是抛异常，一个任务最终都必须归还一个槽位。
                            providerPermits.release();
                        }
                    });
                } catch (RuntimeException exception) {
                    providerPermits.release();
                    log.error("Failed to submit claimed task taskId={}", task.task().taskId(), exception);
                }
            }
        } catch (RuntimeException exception) {
            if (reserved > 0) {
                providerPermits.release(reserved);
            }
            log.error("Task polling failed", exception);
        } finally {
            scheduleNext();
        }
    }

    private int reservePermits() {
        int reserved = 0;
        while (reserved < properties.claimBatchSize() && providerPermits.tryAcquire()) {
            reserved++;
        }
        return reserved;
    }

    private void scheduleNext() {
        if (stopped.get() || !properties.enabled()) {
            return;
        }
        long min = properties.minPollInterval().toMillis();
        long max = properties.maxPollInterval().toMillis();
        // 每轮重新取随机值，使多个 Runner 的轮询时间自然错开。
        long delay = min == max ? min : ThreadLocalRandom.current().nextLong(min, max + 1);
        try {
            taskScheduler.schedule(this::poll, Instant.now().plusMillis(delay));
        } catch (TaskRejectedException exception) {
            // ContextClosedEvent 与当前 poll 可能并发；正常停机时调度器拒绝新任务无需记为系统错误。
            if (!stopped.get()) {
                log.warn("Task scheduler rejected next polling cycle", exception);
            }
        }
    }

    /** Spring 开始关闭上下文时立即置位，早于调度器销毁，避免退出阶段继续安排任务。 */
    @EventListener(ContextClosedEvent.class)
    public void onContextClosed() {
        stop();
    }

    /** 停机后阻止新的轮询被调度；已提交任务由执行器生命周期管理。 */
    @PreDestroy
    public void stop() {
        stopped.set(true);
    }
}
