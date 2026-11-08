package com.example.aitask.api;

import com.example.aitask.model.AiTask;
import com.example.aitask.error.AppException;
import com.example.aitask.error.ErrorCode;
import com.example.aitask.model.GenerateInput;
import com.example.aitask.model.GenerateOptions;
import com.example.aitask.model.TaskStatus;
import com.example.aitask.producer.CreateTaskCommand;
import com.example.aitask.producer.TaskProducer;
import com.example.aitask.storage.AiTaskRepository;
import org.springframework.stereotype.Service;

/**
 * API DTO 与领域对象之间的适配层，让 Controller 不直接依赖存储模型。
 */
@Service
public class TaskApiService {
    private final TaskProducer producer;
    private final AiTaskRepository repository;

    public TaskApiService(TaskProducer producer, AiTaskRepository repository) {
        this.producer = producer;
        this.repository = repository;
    }

    /** 把 HTTP 请求转换为生产者命令并返回新任务摘要。 */
    public CreateTaskResponse create(CreateTaskRequest request) {
        GenerateInput input = new GenerateInput(
                request.input().systemPrompt(), request.input().prompt());
        GenerateOptions options = request.options() == null
                ? null
                : new GenerateOptions(request.options().maxOutputTokens(), request.options().temperature());
        AiTask task = producer.create(new CreateTaskCommand(
                request.type(), request.priority(), request.provider(), request.model(), input, options));
        return new CreateTaskResponse(task.taskId(), task.status(), task.provider(), task.model(), task.createdAt());
    }

    /** 查询任务；不存在时转换为稳定的 TASK_NOT_FOUND 业务错误。 */
    public TaskResponse get(String taskId) {
        return repository.findByTaskId(taskId).map(TaskApiService::toResponse)
                .orElseThrow(TaskApiService::notFound);
    }

    /** 执行幂等取消并返回数据库中的最新状态。 */
    public CancelTaskResponse cancel(String taskId) {
        AiTask task = repository.cancel(taskId).orElseThrow(TaskApiService::notFound);
        return new CancelTaskResponse(task.taskId(), task.status());
    }

    private static TaskResponse toResponse(AiTask task) {
        // result 和 error 都是状态相关视图，避免进行中任务返回误导性数据。
        TaskResponse.Result result = task.status() == TaskStatus.SUCCEEDED
                ? new TaskResponse.Result(task.resultText(), task.finishReason()) : null;
        TaskResponse.Error error = task.lastErrorType() == null
                ? null
                : new TaskResponse.Error(task.lastErrorType(), task.lastErrorCode(), task.lastErrorMessage());
        // total_tokens 可由两个持久化字段稳定计算，因此数据库不重复保存。
        long total = task.inputTokens() + task.outputTokens();
        return new TaskResponse(task.taskId(), task.taskType(), task.status(), task.priority(),
                task.provider(), task.model(), task.attemptCount(), task.maxAttempts(),
                task.createdAt(), task.startedAt(), task.finishedAt(), result, error,
                new TaskResponse.Usage(task.inputTokens(), task.outputTokens(), total));
    }

    private static AppException notFound() {
        return new AppException(ErrorCode.TASK_NOT_FOUND);
    }
}
