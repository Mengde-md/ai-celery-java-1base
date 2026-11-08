package com.example.aitask.model;

/**
 * 跨 Provider 的稳定错误分类；具体 HTTP/厂商错误码另外保存在 error_code。
 */
public enum AiErrorType {
    /** 429 或等价的限流错误，通常可重试。 */
    RATE_LIMITED,
    /** Provider 5xx 或服务暂不可用，通常可重试。 */
    PROVIDER_UNAVAILABLE,
    /** 网络连接或传输异常，通常可重试。 */
    NETWORK_ERROR,
    /** Provider 调用超时或执行租约过期。 */
    TIMEOUT,
    /** Provider 拒绝请求参数，通常不可重试。 */
    INVALID_REQUEST,
    /** 内容安全策略拒绝生成。 */
    CONTENT_REJECTED,
    /** 密钥、权限或账户配置错误。 */
    AUTHENTICATION_ERROR,
    /** 本项目内部错误或无法归类的客户端错误。 */
    INTERNAL_ERROR,
    /** 结果的 UTF-8 字节数超过配置上限。 */
    RESULT_TOO_LARGE
}
