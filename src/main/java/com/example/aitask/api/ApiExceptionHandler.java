package com.example.aitask.api;

import com.example.aitask.error.AppException;
import com.example.aitask.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常出口：对外只返回安全的统一错误码，对内记录请求上下文和完整堆栈。
 */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** 已知业务异常不暴露 cause 和内部 context。 */
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleApplication(
            AppException exception, HttpServletRequest request) {
        ErrorCode code = exception.errorCode();
        log.warn("APP_ERROR | code={} | method={} | uri={} | context={}",
                code.code(), request.getMethod(), request.getRequestURI(), exception.context(), exception);
        return response(code);
    }

    /** 请求格式、Bean Validation 和路径参数错误统一映射为 20001。 */
    @ExceptionHandler({MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(
            Exception exception, HttpServletRequest request) {
        log.warn("REQUEST_INVALID | method={} | uri={} | exceptionType={} | detail={}",
                request.getMethod(), request.getRequestURI(), exception.getClass().getSimpleName(),
                exception.getMessage());
        return response(ErrorCode.PARAM_ERROR);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(
            Exception exception, HttpServletRequest request) {
        log.warn("METHOD_NOT_ALLOWED | method={} | uri={}", request.getMethod(), request.getRequestURI());
        return response(ErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaType(
            Exception exception, HttpServletRequest request) {
        log.warn("MEDIA_TYPE_UNSUPPORTED | method={} | uri={} | contentType={}",
                request.getMethod(), request.getRequestURI(), request.getContentType());
        return response(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(
            DuplicateKeyException exception, HttpServletRequest request) {
        log.error("DB_DUPLICATE | method={} | uri={} | requestId={}",
                request.getMethod(), request.getRequestURI(), MDC.get("requestId"), exception);
        return response(ErrorCode.DATA_DUPLICATE);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDatabase(
            DataAccessException exception, HttpServletRequest request) {
        log.error("DB_ERROR | method={} | uri={} | requestId={}",
                request.getMethod(), request.getRequestURI(), MDC.get("requestId"), exception);
        return response(ErrorCode.DB_ERROR);
    }

    /** 最后的异常保护网：日志保留完整堆栈，响应不泄露实现细节。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(
            Exception exception, HttpServletRequest request) {
        log.error("UNHANDLED_ERROR | method={} | uri={} | requestId={} | exceptionType={}",
                request.getMethod(), request.getRequestURI(), MDC.get("requestId"),
                exception.getClass().getName(), exception);
        return response(ErrorCode.SERVER_ERROR);
    }

    private static ResponseEntity<ApiResponse<Void>> response(ErrorCode code) {
        // 业务接口固定返回 HTTP 200，调用方通过稳定数字 code 判断业务结果。
        return ResponseEntity.ok(ApiResponse.error(code));
    }
}
