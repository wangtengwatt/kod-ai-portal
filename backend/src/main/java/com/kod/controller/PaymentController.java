package com.kod.controller;

import com.kod.common.Result;
import com.kod.dto.*;
import com.kod.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 钱包/支付接口。
 *
 * <p>对标 new-api 钱包页面，提供充值配置、发起支付、充值记录、余额查询等能力。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    // -------------------------------------------------------
    // 充值配置
    // -------------------------------------------------------

    /**
     * 获取充值配置信息。
     */
    @GetMapping("/topup/info")
    public Result<TopUpInfoResponse> getTopUpInfo() {
        return Result.ok(paymentService.getTopUpInfo());
    }

    // -------------------------------------------------------
    // 金额计算
    // -------------------------------------------------------

    /**
     * 计算充值金额（含折扣）。
     */
    @PostMapping("/amount")
    public Result<String> calculateAmount(@Valid @RequestBody AmountRequest req) {
        return Result.ok(paymentService.calculateAmount(req.getAmount()));
    }

    // -------------------------------------------------------
    // 发起支付
    // -------------------------------------------------------

    /**
     * 发起支付（Epay）。
     */
    @PostMapping("/pay")
    public Result<PayResponse> pay(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody PayRequest req) {
        Long userId = paymentService.parseUserIdFromHeader(authorization);
        log.info("发起支付，userId={}, amount={}, method={}", userId, req.getAmount(), req.getPaymentMethod());
        return Result.ok(paymentService.pay(userId, req));
    }

    // -------------------------------------------------------
    // 充值记录
    // -------------------------------------------------------

    /**
     * 分页查询当前用户的充值记录。
     */
    @GetMapping("/topup/self")
    public Result<TopUpPageResponse> getTopUpHistory(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "1") int p,
            @RequestParam(defaultValue = "10") int pageSize) {
        Long userId = paymentService.parseUserIdFromHeader(authorization);
        return Result.ok(paymentService.getTopUpHistory(userId, p, pageSize));
    }

    // -------------------------------------------------------
    // 余额
    // -------------------------------------------------------

    /**
     * 获取钱包余额。
     */
    @GetMapping("/wallet")
    public Result<WalletResponse> getWallet(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Long userId = paymentService.parseUserIdFromHeader(authorization);
        return Result.ok(paymentService.getWallet(userId));
    }

    // -------------------------------------------------------
    // 订单查询
    // -------------------------------------------------------

    /**
     * 查询单笔订单状态（用于前端轮询支付结果）。
     */
    @GetMapping("/order/{orderNo}")
    public Result<OrderStatusResponse> getOrderStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String orderNo) {
        Long userId = paymentService.parseUserIdFromHeader(authorization);
        return Result.ok(paymentService.getOrderStatus(userId, orderNo));
    }
}
