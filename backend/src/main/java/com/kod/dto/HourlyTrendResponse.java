package com.kod.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 看板小时趋势条目（对应 dashboard_hourly 聚合后的数据）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HourlyTrendResponse {

    /** 小时桶（Unix 秒，精确到小时）。 */
    private Long hourBucket;

    /** 该小时内的请求次数。 */
    private Integer requestCount;

    /** 该小时内的配额消耗。 */
    private Integer quota;

    /** 该小时内的 Token 用量。 */
    private Integer tokenUsed;

    /** 该小时内的流式请求次数。 */
    private Integer streamCount;
}
