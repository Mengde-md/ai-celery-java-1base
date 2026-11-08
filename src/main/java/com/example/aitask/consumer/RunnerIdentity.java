package com.example.aitask.consumer;

import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 当前应用实例的 Runner 标识，在整个进程生命周期内保持不变。
 *
 * <p>它写入 lease_owner，便于日志和数据库定位是哪一个实例抢到了任务；真正防止
 * 旧 Runner 回写的是每批随机生成的 lease_token。</p>
 */
@Component
public class RunnerIdentity {
    private final String runnerId = UUID.randomUUID().toString();

    /** 返回当前进程唯一的 Runner ID。 */
    public String runnerId() {
        return runnerId;
    }
}
