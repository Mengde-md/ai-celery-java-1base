package com.example.aitask.model;

/** 一次成功生成的正文和 Provider 结束原因。 */
public record GenerateResult(String text, String finishReason) {
}
