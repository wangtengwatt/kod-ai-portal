package com.kod.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器：将各类异常统一转换为 {@link Result}。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 卡时不足：将自动补足报价返回给客户端展示确认框。
     */
    @ExceptionHandler(CardHourBalanceException.class)
    public Result<Object> handleCardHourBalance(CardHourBalanceException e) {
        log.info("卡时余额不足：code={}, quote={}", e.getCode(), e.getQuote());
        return Result.fail(e.getCode(), e.getMessage(), e.getQuote());
    }

    /**
     * 业务异常。
     */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        log.warn("业务异常：code={}, msg={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验异常（@Valid 失败）。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .orElse("参数校验失败");
        log.warn("参数校验失败：{}", msg);
        return Result.fail(400, msg);
    }

    /**
     * 唯一索引冲突（如邀请码、邮箱重复）。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicate(DuplicateKeyException e) {
        log.warn("唯一约束冲突：{}", e.getMessage());
        return Result.fail(409, "数据已存在（唯一约束冲突）");
    }

    /**
     * 数据库连接或访问失败。不要把驱动、地址、账号等内部细节返回给客户端。
     */
    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result<Void> handleDatabaseUnavailable(DataAccessException e) {
        log.error("数据库暂时不可用", e);
        return Result.fail(503, "数据库暂时不可用，请检查数据库网络连接后重试");
    }

    /**
     * 兜底异常。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return Result.fail(500, "系统异常：" + e.getMessage());
    }
}
