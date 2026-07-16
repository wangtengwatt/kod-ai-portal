package com.kod.common;

import lombok.Getter;

/**
 * 业务异常。
 *
 * <p>用于表达可预期的业务错误（如邀请码无效、密码错误、未授权等），由全局异常处理器统一转换为响应。</p>
 */
@Getter
public class BizException extends RuntimeException {

    /** 业务错误码。 */
    private final int code;

    /**
     * @param code    业务错误码
     * @param message 错误信息
     */
    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 使用默认错误码（400）构造。
     *
     * @param message 错误信息
     */
    public BizException(String message) {
        this(400, message);
    }
}
