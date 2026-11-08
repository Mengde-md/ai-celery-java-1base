package com.example.aitask.producer;

import com.example.aitask.model.GenerateInput;
import com.example.aitask.model.GenerateOptions;

/**
 * API 层交给生产者的创建命令，隔离 HTTP DTO 与领域服务。
 */
public record CreateTaskCommand(
        String type,
        Integer priority,
        String provider,
        String model,
        GenerateInput input,
        GenerateOptions options) {
}
