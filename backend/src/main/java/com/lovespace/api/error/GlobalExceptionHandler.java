package com.lovespace.api.error;

import jakarta.validation.ConstraintViolationException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.slf4j.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.transaction.TransactionSystemException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ApiError.of(ex.getStatus().value(), ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ApiError(OffsetDateTime.now(), 400,
                "VALIDATION_ERROR", "请求参数校验失败", fields));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> constraint(ConstraintViolationException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v -> fields.put(v.getPropertyPath().toString(), v.getMessage()));
        return ResponseEntity.badRequest().body(new ApiError(OffsetDateTime.now(), 400,
                "VALIDATION_ERROR", "请求参数校验失败", fields));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> malformed(Exception ex) {
        return ResponseEntity.badRequest().body(ApiError.of(400, "MALFORMED_REQUEST", "请求格式不正确"));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> methodValidation(HandlerMethodValidationException ex) {
        return ResponseEntity.badRequest().body(ApiError.of(400, "VALIDATION_ERROR", "请求参数校验失败"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> mediaType(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(415).body(ApiError.of(415, "UNSUPPORTED_MEDIA_TYPE", "请求内容类型不支持"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> method(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(405).body(ApiError.of(405, "METHOD_NOT_ALLOWED", "请求方法不支持"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> noResource(NoResourceFoundException ex) {
        return ResponseEntity.status(404).body(ApiError.of(404, "NOT_FOUND", "请求资源不存在"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> uploadTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(413).body(ApiError.of(413, "FILE_TOO_LARGE", "上传文件超过大小限制"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> conflict(DataIntegrityViolationException ex) {
        return ResponseEntity.status(409).body(ApiError.of(409, "DATA_CONFLICT", "数据已存在或正在被使用"));
    }

    @ExceptionHandler({OptimisticLockException.class, PessimisticLockException.class,
            OptimisticLockingFailureException.class, PessimisticLockingFailureException.class,
            CannotAcquireLockException.class})
    ResponseEntity<ApiError> concurrency(Exception ex) {
        log.warn("Concurrent update rejected: {}", ex.getClass().getSimpleName());
        return ResponseEntity.status(409)
                .body(ApiError.of(409, "CONCURRENCY_CONFLICT", "请求正在被其他操作更新，请稍后重试"));
    }

    @ExceptionHandler(TransactionSystemException.class)
    ResponseEntity<ApiError> transaction(TransactionSystemException ex) {
        if (hasCause(ex, OptimisticLockException.class, PessimisticLockException.class,
                OptimisticLockingFailureException.class, PessimisticLockingFailureException.class,
                CannotAcquireLockException.class)) {
            return concurrency(ex);
        }
        return unknown(ex);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unknown(Exception ex) {
        log.error("Unhandled API error", ex);
        return ResponseEntity.internalServerError().body(ApiError.of(500, "INTERNAL_ERROR", "服务器内部错误"));
    }

    private boolean hasCause(Throwable error, Class<?>... types) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            for (Class<?> type : types) {
                if (type.isInstance(current)) return true;
            }
        }
        return false;
    }
}
