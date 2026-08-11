package com.kod.controller;

import com.kod.service.PaymentService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
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
     * 处理回调后重定向到前端钱包页面，携带订单号和状态。
     */
    @GetMapping("/epay/return")
    public void epayReturn(@RequestParam Map<String, String> params, HttpServletResponse response) throws IOException {
        log.info("Epay同步跳转：{}", params);
        paymentService.handleEpayNotify(params);

        // 重定向到前端钱包页，附带订单号和状态供前端展示结果
        String orderNo = params.getOrDefault("out_trade_no", "");
        String status = params.getOrDefault("trade_status", "");
        String frontendUrl = paymentService.getEpayReturnUrl();
        String redirectUrl = frontendUrl
                + (frontendUrl.contains("?") ? "&" : "?")
                + "order_no=" + orderNo
                + "&status=" + status;
        response.sendRedirect(redirectUrl);
    }
}
