package com.kod.common;

import lombok.Getter;

import java.util.Map;

/** 卡时不足时携带自动补足报价的业务异常。 */
@Getter
public class CardHourBalanceException extends BizException {

    /** 人民币足够自动补足，等待用户确认。 */
    public static final int TOP_UP_CONFIRM_REQUIRED = 4601;

    /** 卡时和人民币均不足，可引导用户前往官网充值。 */
    public static final int CNY_BALANCE_INSUFFICIENT = 4602;

    private final Map<String, Object> quote;

    public CardHourBalanceException(int code, String message, Map<String, Object> quote) {
        super(code, message);
        this.quote = quote;
    }
}
