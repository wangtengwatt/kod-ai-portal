package com.kod.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kod.common.BizException;
import com.kod.dto.RelayConfigResponse;
import com.kod.dto.SaveRelayStationRequest;
import com.kod.entity.RelayStation;
import com.kod.entity.RelayStationKey;
import com.kod.entity.User;
import com.kod.mapper.RelayStationKeyMapper;
import com.kod.mapper.RelayStationMapper;
import com.kod.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 中转站服务：保存中转站信息（分两表），以及按用户查询中转站配置。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RelayStationService {

    private final RelayStationMapper relayStationMapper;
    private final RelayStationKeyMapper relayStationKeyMapper;
    private final UserMapper userMapper;

    /**
     * 保存中转站：第一表写 url + 邀请码，第二表写 apiKey + 中转站主键。
     *
     * @param req 保存请求
     * @return 新建的中转站主键
     */
    @Transactional(rollbackFor = Exception.class)
    public Long saveStation(SaveRelayStationRequest req) {
        log.info("保存中转站，url={}, inviteCode={}", req.getUrl(), req.getInviteCode());

        // 邀请码唯一校验（DB 亦有唯一索引兜底）
        Long exists = relayStationMapper.selectCount(
                Wrappers.<RelayStation>lambdaQuery().eq(RelayStation::getInviteCode, req.getInviteCode()));
        if (exists != null && exists > 0) {
            throw new BizException(409, "邀请码已存在：" + req.getInviteCode());
        }

        LocalDateTime now = LocalDateTime.now();
        // 第一表
        RelayStation station = new RelayStation();
        station.setUrl(req.getUrl());
        station.setInviteCode(req.getInviteCode());
        station.setCreateTime(now);
        station.setUpdateTime(now);
        relayStationMapper.insert(station);

        // 第二表
        RelayStationKey key = new RelayStationKey();
        key.setStationId(station.getId());
        key.setApiKey(req.getApiKey());
        key.setCreateTime(now);
        relayStationKeyMapper.insert(key);

        log.info("中转站保存成功，stationId={}", station.getId());
        return station.getId();
    }

    /**
     * 按邀请码查询中转站，供注册时关联使用。
     *
     * @param inviteCode 邀请码
     * @return 中转站；不存在返回 null
     */
    public RelayStation findByInviteCode(String inviteCode) {
        return relayStationMapper.selectOne(
                Wrappers.<RelayStation>lambdaQuery().eq(RelayStation::getInviteCode, inviteCode));
    }

    /**
     * 保存/更新当前用户关联中转站的 API 密钥。
     *
     * <p>每个 station 保留最新一条记录（先删后插），避免历史数据膨胀。</p>
     *
     * @param userId 用户主键
     * @param apiKey API 密钥
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveApiKey(Long userId, String apiKey) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(401, "用户不存在");
        }
        if (user.getStationId() == null) {
            throw new BizException(400, "当前用户未关联中转站，无法配置 API Key");
        }

        // 删除该中转站已有的所有密钥记录，再插入新记录
        relayStationKeyMapper.delete(
                Wrappers.<RelayStationKey>lambdaQuery()
                        .eq(RelayStationKey::getStationId, user.getStationId()));

        RelayStationKey key = new RelayStationKey();
        key.setStationId(user.getStationId());
        key.setApiKey(apiKey);
        key.setCreateTime(LocalDateTime.now());
        relayStationKeyMapper.insert(key);

        log.info("API Key 保存成功，userId={}, stationId={}", userId, user.getStationId());
    }

    /**
     * 根据用户主键获取其关联中转站的 url 与 API 密钥。
     *
     * @param userId 用户主键
     * @return 中转站配置
     */
    public RelayConfigResponse getConfigByUserId(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(401, "用户不存在");
        }
        if (user.getStationId() == null) {
            throw new BizException(404, "当前用户未关联中转站");
        }
        RelayStation station = relayStationMapper.selectById(user.getStationId());
        if (station == null) {
            throw new BizException(404, "关联的中转站不存在");
        }
        // 取该中转站最新的一条密钥（允许为空：未配置密钥时仍可跳转中转站）
        List<RelayStationKey> keys = relayStationKeyMapper.selectList(
                Wrappers.<RelayStationKey>lambdaQuery()
                        .eq(RelayStationKey::getStationId, station.getId())
                        .orderByDesc(RelayStationKey::getId));
        String apiKey = keys.isEmpty() ? "" : keys.get(0).getApiKey();
        log.info("获取中转站配置成功，userId={}, stationId={}, hasApiKey={}", userId, station.getId(), !keys.isEmpty());
        return new RelayConfigResponse(station.getUrl(), apiKey);
    }
}
