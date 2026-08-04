package com.kod.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kod.common.BizException;
import com.kod.entity.RelayStation;
import com.kod.entity.RelayStationKey;
import com.kod.entity.User;
import com.kod.mapper.RelayStationKeyMapper;
import com.kod.mapper.RelayStationMapper;
import com.kod.mapper.UserMapper;
import com.kod.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 客户端会话服务：API Key 选择/释放、余额校验。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final UserMapper userMapper;
    private final RelayStationMapper relayStationMapper;
    private final RelayStationKeyMapper relayStationKeyMapper;
    private final LogSyncService logSyncService;
    private final JwtUtil jwtUtil;

    /**
     * 选择/切换 API Key。
     * - 如果用户 connect 有值（之前连过其他 key）：释放旧 key（status=0），锁定新 key（status=1）
     * - 如果用户 connect 为 null（首次连接）：直接锁定新 key
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> selectKey(Long userId, Long stationId, Long apiKeyId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(401, "用户不存在");
        }

        // 释放旧 key
        if (user.getConnect() != null) {
            RelayStationKey oldKey = relayStationKeyMapper.selectById(user.getConnect());
            if (oldKey != null) {
                oldKey.setStatus(0);
                relayStationKeyMapper.updateById(oldKey);
                log.info("释放旧 key，id={}", oldKey.getId());
            }
        }

        // 校验新 key 是否存在且归属于指定中转站
        RelayStationKey newKey = relayStationKeyMapper.selectById(apiKeyId);
        if (newKey == null) {
            throw new BizException(400, "API Key 不存在");
        }
        if (!newKey.getStationId().equals(stationId)) {
            throw new BizException(400, "API Key 不属于该中转站");
        }
        if (newKey.getStatus() != null && newKey.getStatus() == 1) {
            throw new BizException(409, "该 API Key 正在被占用");
        }

        // 锁定新 key
        newKey.setStatus(1);
        relayStationKeyMapper.updateById(newKey);

        // 更新用户 connect
        // 更新用户 connect（精准更新，避免覆盖 balance 等字段）
        userMapper.update(null,
                Wrappers.<User>lambdaUpdate()
                        .set(User::getConnect, apiKeyId)
                        .eq(User::getId, userId));

        // 停止旧的日志轮询（如果还在跑）
        logSyncService.onUserClose();

        // 查询中转站 URL
        RelayStation station = relayStationMapper.selectById(stationId);
        String url = station != null ? station.getUrl() : "";

        Map<String, Object> result = new HashMap<>();
        result.put("api_key_id", apiKeyId);
        result.put("previous_api_key_id", user.getConnect());
        result.put("url", url);
        result.put("api_key", newKey.getApiKey());

        log.info("选中 key，userId={}, apiKeyId={}, url={}", userId, apiKeyId, url);
        return result;
    }

    /**
     * 释放当前占用的 API Key。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> releaseKey(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(401, "用户不存在");
        }

        Long releasedId = null;
        if (user.getConnect() != null) {
            RelayStationKey key = relayStationKeyMapper.selectById(user.getConnect());
            if (key != null) {
                key.setStatus(0);
                relayStationKeyMapper.updateById(key);
                releasedId = key.getId();
            }
            user.setConnect(null);
            userMapper.update(null,
                    Wrappers.<User>lambdaUpdate()
                            .set(User::getConnect, null)
                            .eq(User::getId, userId));
            log.info("释放 key，userId={}, apiKeyId={}", userId, releasedId);
            // 停止日志轮询
            logSyncService.onUserClose();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("released_api_key_id", releasedId);
        return result;
    }

    /**
     * 获取用户余额和会话状态（含连接详情）。
     */
    public Map<String, Object> getBalance(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(401, "用户不存在");
        }
        BigDecimal balance = user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;
        Map<String, Object> result = new HashMap<>();
        result.put("balance", balance);
        result.put("can_chat", balance.compareTo(BigDecimal.ZERO) > 0);
        result.put("connect", user.getConnect());

        // 补充连接详情，供客户端恢复旧会话时使用
        if (user.getConnect() != null) {
            RelayStationKey connectedKey = relayStationKeyMapper.selectById(user.getConnect());
            if (connectedKey != null) {
                result.put("connected_key_id", connectedKey.getId());
                result.put("connected_api_key", connectedKey.getApiKey());
                result.put("connected_key_status", connectedKey.getStatus());
                result.put("connected_station_id", connectedKey.getStationId());
                RelayStation station = relayStationMapper.selectById(connectedKey.getStationId());
                if (station != null) {
                    result.put("connected_station_url", station.getUrl());
                }
            }
        }

        return result;
    }

    /**
     * 从 Authorization header 解析 userId。
     */
    public Long parseUserIdFromHeader(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new BizException(401, "缺少或非法的 Authorization 头");
        }
        return jwtUtil.parseUserId(authorization.substring("Bearer ".length()));
    }
}
