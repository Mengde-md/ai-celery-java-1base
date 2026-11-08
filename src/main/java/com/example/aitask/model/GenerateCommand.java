package com.example.aitask.model;

/** Runner 交给 AI Provider 的完整、不可变生成命令。 */
public record GenerateCommand(
        String taskId,
        String provider,
        String model,
        GenerateInput input,
        GenerateOptions options) {
}
