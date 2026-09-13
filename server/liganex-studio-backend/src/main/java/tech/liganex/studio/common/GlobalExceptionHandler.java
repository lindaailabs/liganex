package tech.liganex.studio.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一异常处理：业务异常转结构化响应，未知异常只记日志不回传细节。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBiz(BizException ex) {
        return ResponseEntity.status(httpStatusOf(ex.errorCode()))
                .body(ApiResponse.fail(ex.errorCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String field = ex.getBindingResult().getFieldError() == null
                ? "参数" : ex.getBindingResult().getFieldError().getField();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ErrorCode.BAD_REQUEST, "参数校验失败：" + field));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("unexpected error type={} message={}",
                ex.getClass().getName(),
                SensitiveDataSanitizer.sanitize(ex.getMessage()));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR));
    }

    private HttpStatus httpStatusOf(ErrorCode errorCode) {
        int httpLike = errorCode.code() / 100;
        return switch (httpLike) {
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            // 460 段专用于「服务未配置/未就绪」，必须与 500 区分：用户据此判断是配置缺失而非服务故障
            case 460 -> HttpStatus.SERVICE_UNAVAILABLE;
            // 480 段表示上游供应商故障，网关语义
            case 480 -> HttpStatus.BAD_GATEWAY;
            case 400, 410, 420, 430, 470 -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
