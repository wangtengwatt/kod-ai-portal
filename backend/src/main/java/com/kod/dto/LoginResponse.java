package com.kod.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录/注册响应。
 */
@Data
@AllArgsConstructor
public class LoginResponse {

    /** JWT token。 */
    private String token;

    /** 是否为本次新注册的用户。 */
    private boolean newUser;

    /**
     * 中转站同步注册提示。
     * <p>null 表示无需提示（登录或中转站同步成功）；
     * 非 null 表示 kod 注册已成功，但中转站同步失败，需提示用户。</p>
     */
    private String relayMessage;
}
