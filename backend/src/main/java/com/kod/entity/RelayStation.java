package com.kod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 中转站（第一表）。
 *
 * <p>保存中转站 url 与邀请码，邀请码为唯一索引，用于关联注册用户。</p>
 */
@Data
@TableName("relay_station")
public class RelayStation {

    /** 主键（雪花算法生成）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 中转站地址，例如 https://fane.kai.com/v1 。 */
    private String url;

    /** 邀请码（唯一）。 */
    private String inviteCode;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
