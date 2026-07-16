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
}
