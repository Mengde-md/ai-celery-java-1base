package com.example.aitask.model;

/**
 * 与厂商无关的生成参数。provider 和 model 属于任务路由，不在 options 中重复保存。
 */
public record GenerateOptions(Integer maxOutputTokens, Double temperature) {
}
