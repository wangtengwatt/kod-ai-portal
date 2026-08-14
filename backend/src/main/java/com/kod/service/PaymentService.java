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
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
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
    private final ComputeReferralService referralService;

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

        // 优先使用 MAPI 创建固定金额订单，若只有静态收款码则降级 submit.php
        Map<String, String> mapiResult = callEpayMapi(orderNo, req.getAmount(), req.getPaymentMethod());
        String paymentUrl;
        String qrcode = null;
        if (mapiResult != null && StringUtils.hasText(mapiResult.get("pay_url"))) {
            // MAPI 返回了 pay_url（固定金额支付页）
            paymentUrl = mapiResult.get("pay_url");
            qrcode = mapiResult.get("qrcode");
        } else {
            // MAPI 只返回静态收款码或无结果，用 submit.php（URL 自带金额参数）
            log.info("MAPI 无固定金额链接，使用 submit.php");
            paymentUrl = buildEpayUrl(orderNo, req.getAmount(), req.getPaymentMethod());
        }

        log.info("订单创建成功，orderNo={}, userId={}, amount={}", orderNo, userId, req.getAmount());
        return new PayResponse(orderNo, paymentUrl, qrcode);
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
    // 订单查询
    // -------------------------------------------------------

    /**
     * 查询单笔订单状态（用于前端轮询）。
     */
    public OrderStatusResponse getOrderStatus(Long userId, String orderNo) {
        Order order = orderMapper.selectOne(
                Wrappers.<Order>lambdaQuery()
                        .eq(Order::getUserId, userId)
                        .eq(Order::getOrderNo, orderNo));
        if (order == null) {
            throw new BizException(404, "订单不存在");
        }

        OrderStatusResponse resp = new OrderStatusResponse();
        resp.setId(order.getId());
        resp.setUserId(order.getUserId());
        resp.setProductName(order.getProductName());
        resp.setPaymentMethod(order.getPaymentMethod());
        resp.setPaymentProvider(order.getPaymentProvider());
        resp.setAmount(order.getAmount());
        resp.setMoney(order.getActualPayment());
        resp.setOrderNo(order.getOrderNo());
        resp.setStatus(order.getStatus());
        resp.setCouponId(order.getCouponId());
        resp.setCreateTime(order.getCreateTime() != null
                ? order.getCreateTime().toEpochSecond(ZoneOffset.UTC) : 0L);
        resp.setUpdateTime(order.getUpdateTime() != null
                ? order.getUpdateTime().toEpochSecond(ZoneOffset.UTC) : 0L);
        resp.setCompleteTime("success".equals(order.getStatus()) && order.getUpdateTime() != null
                ? order.getUpdateTime().toEpochSecond(ZoneOffset.UTC) : 0L);
        return resp;
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
        // 1. 验证签名
        if (!verifyEpaySign(params)) {
            log.warn("Epay回调签名验证失败：{}", params);
            return "fail";
        }

        // 2. 处理订单
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
                referralService.recordFirstSuccessfulRecharge(order);
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

    /**
     * 获取前端钱包页地址（供回调跳转使用）。
     */
    public String getEpayReturnUrl() {
        return paymentProps.getEpayReturnUrl();
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

    /**
     * 调用易支付 MAPI 创建固定金额支付订单。
     * POST 到 mapi.php，返回 pay_url（WAP支付）和 qrcode（扫码支付）。
     *
     * @return Map 包含 pay_url 和 qrcode，失败返回 null
     */
    private Map<String, String> callEpayMapi(String orderNo, int amount, String paymentMethod) {
        String mapiUrl = paymentProps.getEpayMapiUrl();
        if (!StringUtils.hasText(mapiUrl)) {
            return null;
        }

        String pid = paymentProps.getEpayPid() != null ? paymentProps.getEpayPid() : "";
        String key = paymentProps.getEpayKey() != null ? paymentProps.getEpayKey() : "";
        String notifyUrl = paymentProps.getEpayNotifyUrl() != null ? paymentProps.getEpayNotifyUrl() : "";
        String returnUrl = paymentProps.getEpayReturnUrl() != null ? paymentProps.getEpayReturnUrl() : "";
        String type = "alipay".equals(paymentMethod) ? "alipay" : "wxpay";
        String money = String.valueOf(amount);
        String name = "充值" + amount + "元";

        // 构造签名参数（ASCII 排序）
        Map<String, String> params = new TreeMap<>();
        params.put("money", money);
        params.put("name", name);
        params.put("notify_url", notifyUrl);
        params.put("out_trade_no", orderNo);
        params.put("pid", pid);
        params.put("return_url", returnUrl);
        params.put("type", type);

        StringBuilder signStr = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            signStr.append(e.getKey()).append("=").append(e.getValue()).append("&");
        }
        String stringSignTemp = signStr.substring(0, signStr.length() - 1) + key;
        String sign = md5(stringSignTemp);
        params.put("sign", sign);
        params.put("sign_type", "MD5");

        // 构建 POST body（application/x-www-form-urlencoded，UTF-8 编码）
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (body.length() > 0) body.append("&");
            body.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                .append("=")
                .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }

        try {
            HttpURLConnection conn = (HttpURLConnection) new URI(mapiUrl).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            conn.getOutputStream().write(body.toString().getBytes(StandardCharsets.UTF_8));
            conn.getOutputStream().flush();

            // 读取响应
            byte[] resp = conn.getInputStream().readAllBytes();
            String json = new String(resp, StandardCharsets.UTF_8);
            log.info("Epay MAPI 响应：{}", json);

            // 简易 JSON 解析（避免引入 Jackson 依赖）
            int code = extractJsonInt(json, "code");
            String payUrl = extractJsonString(json, "pay_url");
            String qrcode = extractJsonString(json, "qrcode");

            if (code == 1) {
                Map<String, String> result = new HashMap<>();
                result.put("pay_url", payUrl);
                result.put("qrcode", qrcode);
                log.info("Epay MAPI 成功，orderNo={}", orderNo);
                return result;
            }
            log.warn("Epay MAPI 返回异常，code={}, json={}", code, json);
            return null;
        } catch (Exception e) {
            log.warn("Epay MAPI 调用异常：{}", e.getMessage());
            return null;
        }
    }

    private String buildEpayUrl(String orderNo, int amount, String paymentMethod) {
        // POST 到 submit.php 并跟随重定向，获取最终支付页 URL（金额已预填）
        String submittedUrl = postToSubmitPhp(orderNo, amount, paymentMethod);
        if (submittedUrl != null) {
            return submittedUrl;
        }
        // 降级：返回 GET URL
        return buildEpayGetUrl(orderNo, amount, paymentMethod);
    }

    /**
     * POST 到 submit.php 并捕获最终支付页面 URL。
     * submit.php 重定向后进入支付宝/微信支付页，金额已锁定。
     */
    private String postToSubmitPhp(String orderNo, int amount, String paymentMethod) {
        String baseUrl = paymentProps.getEpayApiUrl() != null
                ? paymentProps.getEpayApiUrl() : "https://mzf.mapay.cc/xpay/epay/submit.php";
        String pid = paymentProps.getEpayPid() != null ? paymentProps.getEpayPid() : "";
        String key = paymentProps.getEpayKey() != null ? paymentProps.getEpayKey() : "";
        String notifyUrl = paymentProps.getEpayNotifyUrl() != null ? paymentProps.getEpayNotifyUrl() : "";
        String returnUrl = paymentProps.getEpayReturnUrl() != null ? paymentProps.getEpayReturnUrl() : "";
        String type = "alipay".equals(paymentMethod) ? "alipay" : "wxpay";
        String money = String.valueOf(amount);
        String name = "充值" + amount + "元";

        Map<String, String> params = new TreeMap<>();
        params.put("money", money);
        params.put("name", name);
        params.put("notify_url", notifyUrl);
        params.put("out_trade_no", orderNo);
        params.put("pid", pid);
        params.put("return_url", returnUrl);
        params.put("type", type);

        StringBuilder signStr = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            signStr.append(e.getKey()).append("=").append(e.getValue()).append("&");
        }
        String stringSignTemp = signStr.substring(0, signStr.length() - 1) + key;
        params.put("sign", md5(stringSignTemp));
        params.put("sign_type", "MD5");

        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (body.length() > 0) body.append("&");
            body.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                .append("=")
                .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }

        try {
            HttpURLConnection conn = (HttpURLConnection) new URI(baseUrl).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            conn.getOutputStream().write(body.toString().getBytes(StandardCharsets.UTF_8));
            conn.getOutputStream().flush();

            int status = conn.getResponseCode();
            if (status >= 300 && status < 400) {
                String location = conn.getHeaderField("Location");
                if (StringUtils.hasText(location)) {
                    String fullUrl = resolveUrl(baseUrl, location);
                    log.info("submit.php 重定向到支付页面：{}", fullUrl);
                    return fullUrl;
                }
            }
            // 也可能是 200 返回 HTML 支付页面
            if (status == 200) {
                String html = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                // 尝试提取 meta refresh 跳转
                int metaIdx = html.indexOf("http-equiv=\"refresh\"");
                if (metaIdx < 0) metaIdx = html.indexOf("http-equiv='refresh'");
                if (metaIdx >= 0) {
                    int urlIdx = html.indexOf("url=", metaIdx);
                    if (urlIdx >= 0) {
                        int endIdx = html.indexOf("\"", urlIdx + 4);
                        if (endIdx < 0) endIdx = html.indexOf("'", urlIdx + 4);
                        if (endIdx < 0) endIdx = html.indexOf("<", urlIdx + 4);
                        String redirectUrl = endIdx > 0
                                ? html.substring(urlIdx + 4, endIdx).trim()
                                : html.substring(urlIdx + 4).trim();
                        if (StringUtils.hasText(redirectUrl)) {
                            String fullUrl = resolveUrl(baseUrl, redirectUrl);
                            log.info("submit.php HTML meta 跳转：{}", fullUrl);
                            return fullUrl;
                        }
                    }
                }
                log.info("submit.php 返回 HTML 页面，长度={}", html.length());
            }
            return null;
        } catch (Exception e) {
            log.warn("submit.php POST 失败：{}", e.getMessage());
            return null;
        }
    }

    /** 解析相对 URL 为绝对 URL。 */
    private String resolveUrl(String baseUrl, String location) {
        if (location.startsWith("http://") || location.startsWith("https://")) {
            return location;
        }
        try {
            URI baseUri = new URI(baseUrl);
            if (location.startsWith("/")) {
                return baseUri.getScheme() + "://" + baseUri.getHost()
                        + (baseUri.getPort() > 0 ? ":" + baseUri.getPort() : "") + location;
            }
            // 相对路径：基于 submit.php 所在目录
            String basePath = baseUri.getPath();
            int lastSlash = basePath.lastIndexOf('/');
            String dir = lastSlash >= 0 ? basePath.substring(0, lastSlash + 1) : "/";
            return baseUri.getScheme() + "://" + baseUri.getHost()
                    + (baseUri.getPort() > 0 ? ":" + baseUri.getPort() : "") + dir + location;
        } catch (Exception e) {
            return location;
        }
    }

    private String buildEpayGetUrl(String orderNo, int amount, String paymentMethod) {
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

    /**
     * 验证 Epay 回调签名。
     * 签名算法与 {@link #buildEpayUrl} 一致：参数按 key ASCII 排序 → 拼接 → 追加密钥 → MD5。
     */
    private boolean verifyEpaySign(Map<String, String> params) {
        String sign = params.get("sign");
        if (!StringUtils.hasText(sign)) {
            log.warn("Epay回调缺少 sign 参数");
            return false;
        }

        String key = paymentProps.getEpayKey();
        if (!StringUtils.hasText(key)) {
            log.warn("Epay密钥未配置，无法验证签名");
            return false;
        }

        // sign、sign_type 不参与签名；空值不参与签名
        Map<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> e : params.entrySet()) {
            String k = e.getKey();
            String v = e.getValue();
            if ("sign".equals(k) || "sign_type".equals(k)) continue;
            if (!StringUtils.hasText(v)) continue;
            sorted.put(k, v);
        }

        StringBuilder signStr = new StringBuilder();
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            signStr.append(e.getKey()).append("=").append(e.getValue()).append("&");
        }
        // 去掉末尾 &
        String stringA = signStr.substring(0, signStr.length() - 1);
        String stringSignTemp = stringA + key;
        String computedSign = md5(stringSignTemp);

        boolean valid = computedSign.equalsIgnoreCase(sign);
        if (!valid) {
            log.warn("Epay签名不匹配，期望={}, 收到={}", computedSign, sign);
        }
        return valid;
    }

    // -------------------------------------------------------
    // 简易 JSON 解析（避免引入 Jackson 依赖）
    // -------------------------------------------------------

    /** 从简单 JSON 中提取整数值。 */
    private int extractJsonInt(String json, String key) {
        String k = "\"" + key + "\"";
        int ki = json.indexOf(k);
        if (ki < 0) return -1;
        int colon = json.indexOf(":", ki);
        if (colon < 0) return -1;
        String sub = json.substring(colon + 1).trim();
        // 跳过引号（字符串类型的数字）
        if (sub.startsWith("\"")) sub = sub.substring(1);
        StringBuilder num = new StringBuilder();
        for (int i = 0; i < sub.length(); i++) {
            char c = sub.charAt(i);
            if (c == '-' || (c >= '0' && c <= '9')) {
                num.append(c);
            } else {
                break;
            }
        }
        try {
            return Integer.parseInt(num.toString());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 从简单 JSON 中提取字符串值。 */
    private String extractJsonString(String json, String key) {
        String k = "\"" + key + "\"";
        int ki = json.indexOf(k);
        if (ki < 0) return null;
        int colon = json.indexOf(":", ki);
        if (colon < 0) return null;
        String sub = json.substring(colon + 1).trim();
        if (sub.startsWith("\"")) {
            int end = sub.indexOf("\"", 1);
            if (end > 0) return sub.substring(1, end);
        }
        // 非引号值
        int end = 0;
        for (int i = 0; i < sub.length(); i++) {
            char c = sub.charAt(i);
            if (c == ',' || c == '}' || c == '\n' || c == '\r') break;
            end = i + 1;
        }
        return sub.substring(0, end);
    }
}
