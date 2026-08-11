package com.kod.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kod.entity.Order;
import com.kod.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务：过期超时未支付的订单。
 * <p>每分钟执行一次，将创建超过 30 分钟仍未支付的订单标记为 expired。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpireTask {

    private final OrderMapper orderMapper;

    /** 订单超时时间（分钟）。 */
    private static final int EXPIRE_MINUTES = 30;

    /**
     * 每分钟执行一次过期扫描。
     */
    @Scheduled(fixedRate = 60_000)
    public void expireStaleOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(EXPIRE_MINUTES);
        List<Order> staleOrders = orderMapper.selectList(
                Wrappers.<Order>lambdaQuery()
                        .eq(Order::getStatus, "pending")
                        .lt(Order::getCreateTime, deadline));

        if (staleOrders.isEmpty()) return;

        for (Order order : staleOrders) {
            order.setStatus("expired");
            order.setUpdateTime(LocalDateTime.now());
            orderMapper.updateById(order);
        }
        log.info("过期订单处理完成，共 {} 笔", staleOrders.size());
    }
}
