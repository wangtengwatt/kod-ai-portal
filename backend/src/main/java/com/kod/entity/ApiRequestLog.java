package com.kod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * new-api 请求日志同步记录。
 */
@Data
@TableName("api_request_logs")
public class ApiRequestLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID。 */
    private Long userId;

    private String requestId;
    private Integer type;
    private String upstreamRequestId;
    private Long createdAt;
    private String modelName;
    private String tokenName;
    private Integer channelId;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer quota;
    private Integer useTime;
    private Integer isStream;
    private String content;
    private String other;
    private String ip;
    private String groupCol;
    private Integer newApiLogId;
    private LocalDateTime syncedAt;
    private String syncBatch;
}
