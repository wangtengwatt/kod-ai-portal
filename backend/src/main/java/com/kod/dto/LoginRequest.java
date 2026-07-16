package com.kod.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录/注册请求（首次登录即注册）。
 */
@Data
public class LoginRequest {

    /** 邮箱。 */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /** 密码。 */
    @NotBlank(message = "密码不能为空")
    private String password;

    /** 邀请码：首次登录（注册）必填，用于关联中转站；老用户登录可不传，且不生效。 */
    private String inviteCode;
}
