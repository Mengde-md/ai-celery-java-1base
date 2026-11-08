package com.example.aitask;

import com.example.aitask.config.AiProperties;
import com.example.aitask.config.SchedulerProperties;
import com.example.aitask.logging.LoggingProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AICelery 启动入口。
 *
 * <p>启动时注册 MyBatis Mapper、类型安全配置以及租约恢复定时任务；TaskPoller
 * 会在应用就绪事件之后开始随机轮询。</p>
 */
@SpringBootApplication
@MapperScan("com.example.aitask.storage.mapper")
@EnableConfigurationProperties({AiProperties.class, SchedulerProperties.class, LoggingProperties.class})
@EnableScheduling
public class AiTaskApplication {

    /** 启动 Spring Boot 应用。 */
    public static void main(String[] args) {
        SpringApplication.run(AiTaskApplication.class, args);
    }
}
