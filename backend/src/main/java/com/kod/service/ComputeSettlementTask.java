package com.kod.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** GPU 固定套餐自动确认、历史预订、待接收转让和测试卡时的定时状态推进。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComputeSettlementTask {

    private final ComputeCenterService computeCenterService;
    private final ComputeTrustService computeTrustService;
    private final ComputeReferralService referralService;

    @Scheduled(fixedDelay = 30_000, initialDelay = 120_000)
    public void advance() {
        if (!computeCenterService.isSchemaAvailable()) return;
        try {
            computeCenterService.advanceScheduledWork();
            referralService.releaseDueRewards();
            computeTrustService.purgeExpiredIdentityDocuments();
        } catch (Exception e) {
            log.error("算力中心定时结算失败", e);
        }
    }
}
