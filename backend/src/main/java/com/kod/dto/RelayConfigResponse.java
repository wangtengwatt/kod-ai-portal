package com.kod.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 中转站配置响应（凭 token 获取）。
 */
@Data
@AllArgsConstructor
public class RelayConfigResponse {

    /** 中转站地址。 */
    private String url;

    /** API 密钥。 */
    private String apiKey;
}
