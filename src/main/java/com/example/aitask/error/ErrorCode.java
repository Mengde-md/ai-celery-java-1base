package com.example.aitask.error;

/**
 * 前后端共享的稳定错误码。
 *
 * <p>msg 只包含可安全对外展示的信息；异常堆栈、Provider 原始错误和数据库细节
 * 只写内部日志。新增错误码时只能追加，已经发布的数字不得复用或改变语义。</p>
 */
public enum ErrorCode {
    SUCCESS(0, "成功"),

    // 20000~29999：接口、HTTP 与请求校验。
    REQUEST_ERROR(20000, "请求处理失败"),
    PARAM_ERROR(20001, "请求参数错误"),
    UNSUPPORTED_TASK_TYPE(20002, "不支持的任务类型"),
    UNSUPPORTED_PROVIDER(20003, "不支持或未启用的模型厂商"),
    UNSUPPORTED_MODEL(20004, "不支持的模型"),
    UNSUPPORTED_OPTION(20005, "模型不支持该生成参数"),
    TASK_NOT_FOUND(20006, "任务不存在"),
    TASK_CANCELLED(20007, "任务已取消"),
    METHOD_NOT_ALLOWED(20008, "请求方法不支持"),
    UNSUPPORTED_MEDIA_TYPE(20009, "请求媒体类型不支持"),
    API_TIMEOUT(20010, "接口请求超时"),
    API_RATE_LIMIT(20011, "接口调用过于频繁"),

    // 30000~39999：数据库与存储。
    DB_ERROR(30000, "数据处理失败"),
    DATA_DUPLICATE(30001, "数据已存在"),
    DATA_NOT_FOUND(30002, "数据不存在"),
    DATA_CONFLICT(30003, "数据状态已发生变化"),

    // 40000~49999：大模型调用。
    LLM_ERROR(40000, "大模型调用失败"),
    LLM_TIMEOUT(40001, "大模型响应超时"),
    LLM_NO_RESPONSE(40002, "大模型未返回有效内容"),
    LLM_CONTENT_VIOLATION(40003, "内容不符合模型安全要求"),
    LLM_TOKEN_LIMIT(40004, "模型上下文长度超限"),
    LLM_RATE_LIMIT(40005, "大模型调用频率超限"),
    LLM_MODEL_ERROR(40006, "模型不存在或暂不可用"),
    LLM_AUTHENTICATION_ERROR(40007, "大模型服务认证失败"),
    LLM_NETWORK_ERROR(40008, "大模型网络请求失败"),
    LLM_PROVIDER_UNAVAILABLE(40009, "大模型服务暂不可用"),
    LLM_RESULT_TOO_LARGE(40010, "大模型返回内容过大"),
    LLM_INVALID_REQUEST(40011, "大模型拒绝了请求"),

    // 50000~59999：系统与服务内部错误。
    SERVER_ERROR(50000, "服务器异常"),
    SYSTEM_BUSY(50001, "系统繁忙，请稍后再试"),
    CONFIG_ERROR(50002, "服务配置错误"),
    SCHEDULER_ERROR(50003, "任务调度异常"),
    INVALID_TASK_PAYLOAD(50004, "任务数据异常"),
    TASK_STATE_CONFLICT(50005, "任务状态已发生变化");

    private final int code;
    private final String msg;

    ErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int code() {
        return code;
    }

    public String msg() {
        return msg;
    }
}
