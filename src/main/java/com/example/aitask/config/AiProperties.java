package com.example.aitask.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * {@code app.ai} 配置映射，集中管理全局调用约束和各 Provider 白名单。
 *
 * @param requestTimeout 单次 Provider HTTP 调用的总超时，V1 默认 120 秒
 * @param maxResultBytes 允许写入数据库的 UTF-8 结果大小上限
 * @param providers      按 provider 名称索引的连接与模型配置
 */
@ConfigurationProperties("app.ai")
public record AiProperties(
        Duration requestTimeout,
        DataSize maxResultBytes,
        Map<String, ProviderProperties> providers) {

    public AiProperties {
        // 构造器默认值保证单元测试或局部配置缺失时仍遵守 V1 安全边界。
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(120) : requestTimeout;
        maxResultBytes = maxResultBytes == null ? DataSize.ofMegabytes(1) : maxResultBytes;
        providers = providers == null ? Map.of() : Map.copyOf(providers);
    }

    /** 单个模型厂商的开关、连接信息和允许使用的真实模型标识。 */
    public record ProviderProperties(
            boolean enabled,
            String baseUrl,
            String apiKey,
            List<String> supportedModels) {

        public ProviderProperties {
            // 对外暴露不可变白名单，避免运行过程中被意外修改。
            supportedModels = supportedModels == null ? List.of() : List.copyOf(supportedModels);
        }
    }
}
