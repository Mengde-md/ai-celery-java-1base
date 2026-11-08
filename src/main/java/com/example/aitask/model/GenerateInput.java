package com.example.aitask.model;

/** 已清理并准备发送给模型的提示词输入。 */
public record GenerateInput(String systemPrompt, String prompt) {
}
