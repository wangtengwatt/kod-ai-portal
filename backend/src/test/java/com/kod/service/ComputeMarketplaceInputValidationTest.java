package com.kod.service;

import com.kod.common.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ComputeMarketplaceInputValidationTest {

    @Test
    void acceptsSingleLineBuyerPublicKey() {
        String key = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIKODMARKETPLACEBUYERKEY000000000000000000 buyer";
        assertEquals(key, ComputeCenterService.normalizeSshPublicKey("  " + key + "  "));
    }

    @Test
    void rejectsPrivateKey() {
        assertThrows(BizException.class, () -> ComputeCenterService.normalizeSshPublicKey(
                "-----BEGIN OPENSSH PRIVATE KEY----- AAAAC3NzaC1lZDI1NTE5AAAAI000000000000000000000000000000000"));
    }

    @Test
    void rejectsMultilinePublicKey() {
        assertThrows(BizException.class, () -> ComputeCenterService.normalizeSshPublicKey(
                "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAI000000000000000000000000000000000\nsecond-line"));
    }
}
