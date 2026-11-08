package com.example.aitask.ai;

import com.example.aitask.error.ErrorCode;
import com.example.aitask.model.AiErrorType;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClientResponseException;

/** 将 Spring AI/HTTP 客户端异常归一化为可对外暴露的稳定错误码。 */
public final class SpringAiExceptionMapper {
    private SpringAiExceptionMapper() {
    }

    /** Provider 原始正文只保留在 cause 中供错误日志排查，不写入 API 响应。 */
    public static AiProviderException map(RuntimeException exception) {
        RestClientResponseException responseException = findCause(exception, RestClientResponseException.class);
        if (responseException != null) {
            return fromStatus(responseException.getStatusCode(), exception);
        }

        String fingerprint = exceptionFingerprint(exception);
        if (containsAny(fingerprint, "content_filter", "content policy", "safety violation")) {
            return error(ErrorCode.LLM_CONTENT_VIOLATION, AiErrorType.INVALID_REQUEST,
                    "CONTENT_VIOLATION", false, exception);
        }
        if (containsAny(fingerprint, "context_length", "maximum context", "token limit")) {
            return error(ErrorCode.LLM_TOKEN_LIMIT, AiErrorType.INVALID_REQUEST,
                    "TOKEN_LIMIT", false, exception);
        }
        if (containsAny(fingerprint, "modelnotfound", "model_not_found", "model not found")) {
            return error(ErrorCode.LLM_MODEL_ERROR, AiErrorType.INVALID_REQUEST,
                    "MODEL_NOT_FOUND", false, exception);
        }
        if (containsAny(fingerprint, "ratelimit", "too many requests")) {
            return error(ErrorCode.LLM_RATE_LIMIT, AiErrorType.RATE_LIMITED,
                    "HTTP_429", true, exception);
        }
        if (containsAny(fingerprint, "authentication", "permissiondenied")) {
            return error(ErrorCode.LLM_AUTHENTICATION_ERROR, AiErrorType.AUTHENTICATION_ERROR,
                    "HTTP_401", false, exception);
        }
        if (containsAny(fingerprint, "badrequest", "unprocessable")) {
            return error(ErrorCode.LLM_INVALID_REQUEST, AiErrorType.INVALID_REQUEST,
                    "HTTP_400", false, exception);
        }
        if (containsAny(fingerprint, "internalserver", "serviceunavailable")) {
            return error(ErrorCode.LLM_PROVIDER_UNAVAILABLE, AiErrorType.PROVIDER_UNAVAILABLE,
                    "HTTP_5XX", true, exception);
        }
        if (hasCause(exception, SocketTimeoutException.class) || hasCause(exception, TimeoutException.class)) {
            return error(ErrorCode.LLM_TIMEOUT, AiErrorType.TIMEOUT,
                    "REQUEST_TIMEOUT", true, exception);
        }
        if (hasCause(exception, IOException.class)) {
            return error(ErrorCode.LLM_NETWORK_ERROR, AiErrorType.NETWORK_ERROR,
                    "NETWORK_IO", true, exception);
        }
        return error(ErrorCode.LLM_ERROR, AiErrorType.INTERNAL_ERROR,
                "PROVIDER_CALL_FAILED", false, exception);
    }

    private static AiProviderException fromStatus(HttpStatusCode status, RuntimeException cause) {
        int value = status.value();
        if (value == 429) {
            return error(ErrorCode.LLM_RATE_LIMIT, AiErrorType.RATE_LIMITED,
                    "HTTP_429", true, cause);
        }
        if (value >= 500) {
            return error(ErrorCode.LLM_PROVIDER_UNAVAILABLE, AiErrorType.PROVIDER_UNAVAILABLE,
                    "HTTP_" + value, true, cause);
        }
        if (value == 401 || value == 403) {
            return error(ErrorCode.LLM_AUTHENTICATION_ERROR, AiErrorType.AUTHENTICATION_ERROR,
                    "HTTP_" + value, false, cause);
        }
        return error(ErrorCode.LLM_INVALID_REQUEST, AiErrorType.INVALID_REQUEST,
                "HTTP_" + value, false, cause);
    }

    private static AiProviderException error(ErrorCode code, AiErrorType type,
                                             String providerCode, boolean retryable,
                                             RuntimeException cause) {
        return new AiProviderException(code, type, providerCode, retryable, null, cause);
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        return findCause(throwable, type) != null;
    }

    private static <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private static String exceptionFingerprint(Throwable throwable) {
        StringBuilder value = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            value.append(current.getClass().getSimpleName()).append(' ')
                    .append(current.getMessage()).append(' ');
            current = current.getCause();
        }
        return value.toString().toLowerCase(Locale.ROOT);
    }
}
