package com.kod.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户配置 API Key 请求（已登录用户为关联的中转站设置/更新密钥）。
 */
@Data
public class SaveApiKeyRequest {

    /** API 密钥，例如 sk-xxxx 。 */
    @NotBlank(message = "apiKey 不能为空")
    private String apiKey;
}
