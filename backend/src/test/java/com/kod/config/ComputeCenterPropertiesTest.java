package com.kod.config;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComputeCenterPropertiesTest {

    @Test
    void normalizesAdminEmailsAndKaiDomains() {
        ComputeCenterProperties properties = new ComputeCenterProperties(
                " Admin@Kai.com,USER@example.com ",
                "kai.com, KAIWEB.ORG",
                new BigDecimal("1.002"),
                new BigDecimal("1000.000"),
                "delivery-secret", "./target/test-private", false, false,
                false, "", "", "http://127.0.0.1:8080/api/compute/proxy/v1/",
                new BigDecimal("7.2000"));

        assertTrue(properties.isAdminEmail("admin@kai.com"));
        assertTrue(properties.isAdminEmail(" USER@EXAMPLE.COM "));
        assertFalse(properties.isAdminEmail("other@example.com"));
        assertTrue(properties.getKaiStationDomains().contains("kaiweb.org"));
        assertTrue(properties.getProxyBaseUrl().endsWith("/proxy/v1"));
    }
}
