package com.kod.controller;

import com.kod.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付回调接口（无需认证，由支付平台签名验证）。
 */
@Slf4j
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentCallbackController {

    private final PaymentService paymentService;

    /**
     * Epay 支付异步回调。
     * 同时支持 GET 和 POST，Epay 会以 GET 方式回调。
     */
    @RequestMapping(
        value = "/epay/notify",
        method = {RequestMethod.GET, RequestMethod.POST}
    )
    public String epayNotify(@RequestParam Map<String, String> params) {
        log.info("收到Epay回调：{}", params);
        return paymentService.handleEpayNotify(params);
    }

    /**
     * Epay 支付同步跳转（用户支付完成后浏览器跳回）。
     */
    @GetMapping("/epay/return")
    public void epayReturn(@RequestParam Map<String, String> params) {
        log.info("Epay同步跳转：{}", params);
        paymentService.handleEpayNotify(params);
    }
}
