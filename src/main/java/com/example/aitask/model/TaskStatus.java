package com.example.aitask.model;

/** Task 核心状态机。V1 不设置 WAITING_RETRY，重试任务直接回到 PENDING。 */
public enum TaskStatus {
    /** 等待 Runner 抢占，也包括等待下一次重试的任务。 */
    PENDING,
    /** 已被某个 Runner 持有有效租约并正在执行。 */
    RUNNING,
    /** 模型结果已通过租约校验并成功持久化。 */
    SUCCEEDED,
    /** 不可重试或已经耗尽最大尝试次数。 */
    FAILED,
    /** 用户已取消，后续旧 Runner 回写会因状态不匹配而失败。 */
    CANCELLED;

    /** 判断当前状态是否不可再发生业务状态迁移。 */
    public boolean terminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }
}
