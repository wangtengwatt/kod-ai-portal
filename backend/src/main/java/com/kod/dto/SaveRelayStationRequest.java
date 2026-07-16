package com.kod.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 保存 AI 中转站请求。
 */
@Data
public class SaveRelayStationRequest {

    /** 中转站地址，例如 https://fane.kai.com/v1 。 */
    @NotBlank(message = "中转站 url 不能为空")
    private String url;

    /** API 密钥，例如 sk-xxxx 。 */
    @NotBlank(message = "apiKey 不能为空")
    private String apiKey;

    /** 邀请码（唯一）。 */
    @NotBlank(message = "邀请码不能为空")
    private String inviteCode;
}
