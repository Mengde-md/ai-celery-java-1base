package com.example.aitask.api;

import com.example.aitask.model.TaskStatus;

/** 取消接口返回的任务标识和最终可见状态。 */
public record CancelTaskResponse(String taskId, TaskStatus status) {
}
