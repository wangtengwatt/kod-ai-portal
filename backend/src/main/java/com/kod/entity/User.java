package com.kod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户。
 *
 * <p>邮箱唯一；密码 BCrypt 加密存储；{@link #stationId} 在注册（首登）时由邀请码解析并固化，
 * 用于关联该用户可访问的中转站。</p>
 */
@Data
@TableName("sys_user")
public class User {

    /** 主键（雪花算法生成）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 登录邮箱（唯一）。 */
    private String email;

    /** BCrypt 加密后的密码。 */
    private String password;

    /** 关联的中转站主键（注册时由邀请码解析）。 */
    private Long stationId;

    /** 余额（元）。 */
    private java.math.BigDecimal balance;

    /** 历史累计消耗（元）。 */
    private java.math.BigDecimal historicalConsumption;

    /** 当前连接的 apikey_id，FK → relay_station_key.id。 */
    private Long connect;

    /** 创建时间。 */
    private LocalDateTime createTime;
}
