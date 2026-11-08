package com.example.aitask.logging;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 捕获未被 Spring MVC 或 Runner 边界处理的线程级致命异常。 */
@Component
public class GlobalUncaughtExceptionLogger {
    private static final Logger log = LoggerFactory.getLogger(GlobalUncaughtExceptionLogger.class);
    private Thread.UncaughtExceptionHandler previous;

    @PostConstruct
    void install() {
        previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
                log.error("UNCAUGHT_EXCEPTION | thread={} | exceptionType={}",
                        thread.getName(), throwable.getClass().getName(), throwable));
    }

    @PreDestroy
    void restore() {
        Thread.setDefaultUncaughtExceptionHandler(previous);
    }
}
