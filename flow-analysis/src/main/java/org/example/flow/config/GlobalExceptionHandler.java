package org.example.flow.config;

import org.example.flow.model.ApiResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.format.DateTimeParseException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResp<Void> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("非法参数: {}", ex.getMessage());
        return ApiResp.error(400, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResp<Void> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String msg = String.format("参数 '%s' 值非法: %s", ex.getName(), ex.getValue());
        log.warn("参数类型不匹配: {}", msg);
        return ApiResp.error(400, msg);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResp<Void> handleMissingParam(MissingServletRequestParameterException ex) {
        String msg = String.format("缺少必填参数: %s", ex.getParameterName());
        log.warn("缺少参数: {}", msg);
        return ApiResp.error(400, msg);
    }

    @ExceptionHandler(DateTimeParseException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResp<Void> handleDateTimeParse(DateTimeParseException ex) {
        log.warn("时间解析失败: {}", ex.getMessage());
        return ApiResp.error(400, "时间格式非法，请使用 yyyy-MM-dd'T'HH:mm:ss（如 2026-06-01T00:00:00）");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResp<Void> handleGeneric(Exception ex) {
        log.error("服务器内部错误", ex);
        return ApiResp.error(500, "服务器内部错误");
    }
}
