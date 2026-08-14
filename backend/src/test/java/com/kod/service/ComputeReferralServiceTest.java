package com.kod.service;

import com.kod.common.BizException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ComputeReferralServiceTest {

    @Test
    void calculatesFivePercentAndAppliesOneHundredYuanCap() {
        assertEquals(new BigDecimal("5.0000"),
                ComputeReferralService.calculateReward(new BigDecimal("100.00")));
        assertEquals(new BigDecimal("100.0000"),
                ComputeReferralService.calculateReward(new BigDecimal("10000.00")));
    }

    @Test
    void masksInvitedUserEmail() {
        assertEquals("ab***@example.com", ComputeReferralService.maskEmail("abcdef@example.com"));
        assertEquals("a***@example.com", ComputeReferralService.maskEmail("a@example.com"));
    }

    @Test
    void validatesInviteCodeAndHashesDeviceWithoutPersistingRawId() {
        String code = "0123456789abcdef0123456789abcdef";
        assertEquals(code, ComputeReferralService.normalizeInviteCode(code.toUpperCase()));
        assertEquals(64, ComputeReferralService.hashDeviceId("device-12345678").length());
        assertThrows(BizException.class, () -> ComputeReferralService.normalizeInviteCode("login-invite-code"));
        assertThrows(BizException.class, () -> ComputeReferralService.hashDeviceId("short"));
    }
}
