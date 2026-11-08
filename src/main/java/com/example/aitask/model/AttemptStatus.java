package com.example.aitask.model;

/**
 * 一次实际执行的结论。Attempt 只记录结果，因此没有 PENDING 或 RUNNING 状态。
 */
public enum AttemptStatus {
    SUCCEEDED,
    FAILED
}
