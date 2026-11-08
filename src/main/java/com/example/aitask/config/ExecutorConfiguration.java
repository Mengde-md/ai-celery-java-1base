package com.example.aitask.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Runner 执行器、并发闸门和轻量调度线程的基础设施配置。
 */
@Configuration(proxyBeanMethods = false)
public class ExecutorConfiguration {

    /**
     * 每个已抢占任务使用一个虚拟线程，适合等待模型 HTTP 响应的阻塞型工作负载。
     */
    @Bean(destroyMethod = "shutdown")
    ExecutorService taskExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 全局并发槽位。Poller 必须先获得槽位才允许抢占，避免抢到任务后长时间排队。
     */
    @Bean
    Semaphore providerPermits(SchedulerProperties properties) {
        return new Semaphore(properties.maxConcurrency());
    }

    /**
     * 只负责触发轮询和租约恢复，不在该线程池内执行耗时的模型调用。
     */
    @Bean
    TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("aicelery-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.initialize();
        return scheduler;
    }
}
