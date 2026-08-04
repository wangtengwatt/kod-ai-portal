package com.kod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 按模型汇总累计（看板卡片数据源）。
 */
@Data
@TableName("dashboard_model_summary")
public class DashboardModelSummary {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID。 */
    private Long userId;

    private String modelName;
    private Integer totalRequests;
    private Integer totalQuota;
    private Integer totalPrompt;
    private Integer totalCompletion;
    private Integer totalTokens;
    private Integer totalUseTime;
    private Integer totalStream;
    private Long lastRequestAt;
    private LocalDateTime updatedAt;
}
