package com.example.aitask.api;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * V1 任务 HTTP 入口，只负责协议映射，不承载调度业务逻辑。
 */
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {
    private final TaskApiService service;

    public TaskController(TaskApiService service) {
        this.service = service;
    }

    /**
     * 创建异步任务。此时只保证任务已持久化，不等待模型执行完成。
     */
    @PostMapping
    public ApiResponse<CreateTaskResponse> create(
            @Valid @RequestBody CreateTaskRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    /** 按服务端生成的 taskId 查询任务当前状态和结果。 */
    @GetMapping("/{taskId}")
    public ApiResponse<TaskResponse> get(@PathVariable String taskId) {
        return ApiResponse.ok(service.get(taskId));
    }

    /**
     * 取消待执行或正在执行的任务；对已经进入终态的任务保持幂等。
     */
    @PostMapping("/{taskId}/cancel")
    public ApiResponse<CancelTaskResponse> cancel(@PathVariable String taskId) {
        return ApiResponse.ok(service.cancel(taskId));
    }
}
