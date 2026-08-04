package com.kod.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 看板模型汇总条目（对应 dashboard_model_summary 表）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryResponse {

    /** 模型名称。 */
    private String modelName;

    /** 总请求次数。 */
    private Integer totalRequests;

    /** 总配额消耗。 */
    private Integer totalQuota;

    /** 总提示词 Token 数。 */
    private Integer totalPrompt;

    /** 总补全 Token 数。 */
    private Integer totalCompletion;

    /** 总 Token 用量（prompt + completion）。 */
    private Integer totalTokens;

    /** 总耗时（秒）。 */
    private Integer totalUseTime;

    /** 总流式请求次数。 */
    private Integer totalStream;

    /** 最近一次请求时间（Unix 秒）。 */
    private Long lastRequestAt;
}
