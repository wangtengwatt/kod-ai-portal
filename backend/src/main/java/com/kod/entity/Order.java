package com.kod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单。
 */
@Data
@TableName("orders")
public class Order {

    /** 自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID。 */
    private Long userId;

    /** 商品名称。 */
    private String productName;

    /** 支付方式：alipay/wxpay/stripe/creem。 */
    private String paymentMethod;

    /** 支付提供商：epay/stripe/creem/waffo/waffo_pancake。 */
    private String paymentProvider;

    /** 所需金额（元）。 */
    private BigDecimal amount;

    /** 实付金额（元）。 */
    private BigDecimal actualPayment;

    /** 订单号（唯一）。 */
    private String orderNo;

    /** 订单状态：pending/success/failed/expired。 */
    private String status;

    /** 优惠券ID。 */
    private Long couponId;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 修改时间。 */
    private LocalDateTime updateTime;
}
