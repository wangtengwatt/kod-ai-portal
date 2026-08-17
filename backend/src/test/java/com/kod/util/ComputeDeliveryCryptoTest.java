package com.kod.util;

import com.kod.common.BizException;
import com.kod.config.ComputeCenterProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ComputeDeliveryCryptoTest {

    @Test
    void encryptsAndDecryptsDeliveryInformation() {
        ComputeDeliveryCrypto crypto = crypto("a-production-secret-that-is-long-enough");
        String plaintext = "ssh user@10.0.0.8 / Jupyter token";

        String ciphertext = crypto.encrypt(plaintext);

        assertNotEquals(plaintext, ciphertext);
        assertEquals(plaintext, crypto.decrypt(ciphertext));
    }

    @Test
    void refusesToStoreDeliveryInformationWithoutSecret() {
        assertThrows(BizException.class, () -> crypto("").encrypt("ssh user@host"));
        assertThrows(BizException.class, () -> crypto("too-short").encrypt("ssh user@host"));
    }

    private static ComputeDeliveryCrypto crypto(String secret) {
        return new ComputeDeliveryCrypto(new ComputeCenterProperties(
                "", "kai.com", new BigDecimal("1.002"), new BigDecimal("1000.000"), secret,
                "./target/test-private", false, false, false, "", "",
                "http://127.0.0.1:8080/api/compute/proxy/v1", new BigDecimal("7.2000")));
    }
}
