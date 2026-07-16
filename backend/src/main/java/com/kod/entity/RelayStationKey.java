package com.kod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 中转站 API 密钥（第二表）。
 *
 * <p>通过 {@link #stationId} 关联 {@link RelayStation} 主键。</p>
 */
@Data
@TableName("relay_station_key")
public class RelayStationKey {

    /** 主键（雪花算法生成）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属中转站主键（关联 relay_station.id）。 */
    private Long stationId;

    /** API 密钥，例如 sk-xxxx 。 */
    private String apiKey;

    /** 创建时间。 */
    private LocalDateTime createTime;
}
