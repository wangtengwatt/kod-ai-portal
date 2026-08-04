package com.kod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 按模型+小时聚合的用量明细。
 */
@Data
@TableName("dashboard_hourly")
public class DashboardHourly {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID。 */
    private Long userId;

    private String modelName;
    private Long hourBucket;
    private Integer channelId;
    private Integer requestCount;
    private Integer quota;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer tokenUsed;
    private Integer useTime;
    private Integer streamCount;
}
