package com.kod.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kod.common.BizException;
import com.kod.config.PaymentProperties;
import com.kod.dto.*;
import com.kod.entity.Order;
import com.kod.entity.User;
import com.kod.mapper.OrderMapper;
import com.kod.mapper.UserMapper;
import com.kod.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 钱包/支付服务：充值配置、创建订单、充值记录、余额查询。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final UserMapper userMapper;
    private final OrderMapper orderMapper;
    private final JwtUtil jwtUtil;
    private final PaymentProperties paymentProps;

    // -------------------------------------------------------
    // 充值配置
    // -------------------------------------------------------

    /**
     * 获取充值配置信息。
     */
    public TopUpInfoResponse getTopUpInfo() {
        List<TopUpInfoResponse.PayMethod> methods = paymentProps.getPayMethods().stream()
                .map(m -> new TopUpInfoResponse.PayMethod(
                        m.getName(), m.getType(), m.getColor(), m.getMinTopup()))
                .collect(Collectors.toList());

        return new TopUpInfoResponse(
                paymentProps.isEpayEnabled(),
                paymentProps.isStripeEnabled(),
                paymentProps.isCreemEnabled(),
                paymentProps.isWaffoEnabled(),
                paymentProps.isWaffoPancakeEnabled(),
                paymentProps.isComplianceConfirmed(),
                paymentProps.getComplianceTermsVersion(),
                paymentProps.getMinTopup(),
                paymentProps.getStripeMinTopup(),
                paymentProps.getWaffoMinTopup(),
                paymentProps.getWaffoPancakeMinTopup(),
                paymentProps.getAmountOptions(),
                paymentProps.getDiscount(),
                methods,
                "",
                null,
                "[]"
        );
    }

    // -------------------------------------------------------
    // 金额计算
    // -------------------------------------------------------

    /**
     * 计算实际支付金额（含折扣）。
     * 返回保留两位小数的字符串。
     */
    public String calculateAmount(int amount) {
        Double discountRate = null;
        if (paymentProps.getDiscount() != null) {
            discountRate = paymentProps.getDiscount().get(amount);
        }
        BigDecimal payMoney;
        if (discountRate != null && discountRate > 0 && discountRate < 1) {
            payMoney = BigDecimal.valueOf(amount)
                    .multiply(BigDecimal.valueOf(discountRate))
                    .setScale(2, RoundingMode.HALF_UP);
        } else {
            payMoney = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
        }
        return payMoney.toPlainString();
    }

    // -------------------------------------------------------
    // 发起支付
    // -------------------------------------------------------

    /**
     * 发起支付（Epay），创建订单并返回支付链接。
     */
    @Transactional(rollbackFor = Exception.class)
    public PayResponse pay(Long userId, PayRequest req) {
        // 校验支付方式
        boolean methodValid = paymentProps.getPayMethods().stream()
                .anyMatch(m -> m.getType().equals(req.getPaymentMethod()));
        if (!methodValid) {
            throw new BizException(400, "不支持的支付方式：" + req.getPaymentMethod());
        }

        String orderNo = generateOrderNo(userId);
        String payMoney = calculateAmount(req.getAmount());

        Order order = new Order();
        order.setUserId(userId);
        order.setProductName("KOD Token 充值 " + req.getAmount() + " 元");
        order.setPaymentMethod(req.getPaymentMethod());
        order.setPaymentProvider("epay");
        order.setAmount(BigDecimal.valueOf(req.getAmount()));
        order.setActualPayment(new BigDecimal(payMoney));
        order.setOrderNo(orderNo);
        order.setStatus("pending");
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.insert(order);

        // 构造支付 URL — 使用 MAPI 方式，POST 获取支付链接后再跳转
        // 直接构造 submit.php 的 GET URL 并返回前端跳转
        String paymentUrl = buildEpayUrl(orderNo, req.getAmount(), req.getPaymentMethod());

        log.info("订单创建成功，orderNo={}, userId={}, amount={}", orderNo, userId, req.getAmount());
        return new PayResponse(orderNo, paymentUrl);
    }

    // -------------------------------------------------------
    // 充值记录
    // -------------------------------------------------------

    /**
     * 分页查询当前用户的充值记录。
     */
    public TopUpPageResponse getTopUpHistory(Long userId, int page, int pageSize) {
        Page<Order> p = new Page<>(page, pageSize);
        Page<Order> result = orderMapper.selectPage(p,
                Wrappers.<Order>lambdaQuery()
                        .eq(Order::getUserId, userId)
                        .orderByDesc(Order::getCreateTime));

        List<TopUpItemResponse> items = result.getRecords().stream().map(o -> {
            TopUpItemResponse item = new TopUpItemResponse();
            item.setId(o.getId());
            item.setUserId(o.getUserId());
            item.setAmount(o.getAmount() != null ? o.getAmount().intValue() : 0);
            item.setMoney(o.getActualPayment());
            item.setTradeNo(o.getOrderNo());
            item.setPaymentMethod(o.getPaymentMethod());
            item.setPaymentProvider(o.getPaymentProvider());
            item.setCreateTime(o.getCreateTime() != null
                    ? o.getCreateTime().toEpochSecond(ZoneOffset.UTC) : 0L);
            item.setCompleteTime("success".equals(o.getStatus()) && o.getUpdateTime() != null
                    ? o.getUpdateTime().toEpochSecond(ZoneOffset.UTC) : 0L);
            item.setStatus(o.getStatus());
            return item;
        }).collect(Collectors.toList());

        return new TopUpPageResponse(items, result.getTotal(), page, pageSize);
    }

    // -------------------------------------------------------
    // 余额
    // -------------------------------------------------------

    /**
     * 获取钱包余额。
     */
    public WalletResponse getWallet(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(401, "用户不存在");
        }
        BigDecimal balance = user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;
        BigDecimal consumption = user.getHistoricalConsumption() != null
                ? user.getHistoricalConsumption() : BigDecimal.ZERO;
        return new WalletResponse(balance, consumption);
    }

    // -------------------------------------------------------
    // Epay 回调
    // -------------------------------------------------------

    /**
     * 处理 Epay 支付回调。
     *
     * @return 成功返回 "success"，失败返回 "fail"
     */
    @Transactional(rollbackFor = Exception.class)
    public String handleEpayNotify(Map<String, String> params) {
        // 简化实现：校验签名 → 处理订单
        String tradeNo = params.get("out_trade_no");
        String tradeStatus = params.get("trade_status");
        String money = params.get("money");

        if (!StringUtils.hasText(tradeNo)) {
            log.warn("Epay回调缺少 out_trade_no");
            return "fail";
        }

        Order order = orderMapper.selectOne(
                Wrappers.<Order>lambdaQuery().eq(Order::getOrderNo, tradeNo));
        if (order == null) {
            log.warn("Epay回调订单不存在：{}", tradeNo);
            return "fail";
        }

        if (!"pending".equals(order.getStatus())) {
            // 幂等：已处理过的订单直接返回成功
            return "success";
        }

        if ("TRADE_SUCCESS".equals(tradeStatus)) {
            // 更新订单状态
            order.setStatus("success");
            if (StringUtils.hasText(money)) {
                order.setActualPayment(new BigDecimal(money));
            }
            order.setUpdateTime(LocalDateTime.now());
            orderMapper.updateById(order);

            // 增加用户余额
            User user = userMapper.selectById(order.getUserId());
            if (user != null) {
                BigDecimal addAmount = order.getAmount();
                BigDecimal newBalance = (user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO)
                        .add(addAmount != null ? addAmount : BigDecimal.ZERO);
                user.setBalance(newBalance);
                userMapper.updateById(user);
                log.info("充值成功，userId={}, orderNo={}, amount={}, newBalance={}",
                        user.getId(), tradeNo, addAmount, newBalance);
            }
            return "success";
        }

        // 支付失败
        order.setStatus("failed");
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
        return "fail";
    }

    // -------------------------------------------------------
    // 解析 JWT 获取 userId
    // -------------------------------------------------------

    /**
     * 从 Authorization header 解析 userId。
     */
    public Long parseUserIdFromHeader(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new BizException(401, "缺少或非法的 Authorization 头");
        }
        return jwtUtil.parseUserId(authorization.substring("Bearer ".length()));
    }

    // -------------------------------------------------------
    // private helpers
    // -------------------------------------------------------

    private String generateOrderNo(Long userId) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uid = String.format("%04d", userId % 10000);
        String random = String.format("%04d", new Random().nextInt(10000));
        return "KOD" + timestamp + uid + random;
    }

    private String buildEpayUrl(String orderNo, int amount, String paymentMethod) {
        String baseUrl = paymentProps.getEpayApiUrl() != null
                ? paymentProps.getEpayApiUrl() : "https://mzf.mapay.cc/xpay/epay/submit.php";
        String pid = paymentProps.getEpayPid() != null ? paymentProps.getEpayPid() : "";
        String key = paymentProps.getEpayKey() != null ? paymentProps.getEpayKey() : "";
        String notifyUrl = paymentProps.getEpayNotifyUrl() != null
                ? paymentProps.getEpayNotifyUrl() : "";
        String returnUrl = paymentProps.getEpayReturnUrl() != null
                ? paymentProps.getEpayReturnUrl() : "";
        String type = "alipay".equals(paymentMethod) ? "alipay" : "wxpay";
        String money = String.valueOf(amount);

        // Epay MD5 签名算法：
        // 1. 将所有参数按照参数名 ASCII 码从小到大排序（a-z）
        // 2. sign、sign_type、空值不参与签名
        // 3. 拼接成 key=value&key=value 格式
        // 4. 拼接商户密钥 KEY，MD5 加密得出 sign（小写）
        Map<String, String> params = new TreeMap<>();
        params.put("money", money);
        params.put("name", "充值" + amount + "元");
        params.put("notify_url", notifyUrl);
        params.put("out_trade_no", orderNo);
        params.put("pid", pid);
        params.put("return_url", returnUrl);
        params.put("type", type);

        StringBuilder signStr = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            signStr.append(e.getKey()).append("=").append(e.getValue()).append("&");
        }
        // 去掉末尾 &
        String stringA = signStr.substring(0, signStr.length() - 1);
        // 拼接密钥
        String stringSignTemp = stringA + key;
        String sign = md5(stringSignTemp);

        // 拼接最终 URL — 参数值不进行 URL 编码
        return baseUrl + "?pid=" + pid
                + "&type=" + type
                + "&out_trade_no=" + orderNo
                + "&notify_url=" + notifyUrl
                + "&return_url=" + returnUrl
                + "&name=" + params.get("name")
                + "&money=" + money
                + "&sign=" + sign
                + "&sign_type=MD5";
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("MD5 签名失败", e);
        }
    }
}
