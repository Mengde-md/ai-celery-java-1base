package com.example.aitask.model;

import java.time.Instant;

/**
 * 已被当前 Runner 抢占的 Task 快照。
 *
 * @param task             包含本次 lease_token 和递增后 attempt_count 的任务
 * @param attemptStartedAt 本次抢占完成时间，用于计算 Attempt 耗时
 */
public record ClaimedTask(AiTask task, Instant attemptStartedAt) {
}
