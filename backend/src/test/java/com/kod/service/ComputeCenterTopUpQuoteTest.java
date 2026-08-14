package com.kod.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComputeCenterTopUpQuoteTest {

    @Test
    void roundsShortageUpToOneTenthAndKeepsTheExtraCardHours() {
        Map<String, Object> quote = ComputeCenterService.buildCardHourTopUpQuote(
                new BigDecimal("7.001"), new BigDecimal("6.900"),
                new BigDecimal("1.0020"), new BigDecimal("100.0000"));

        assertEquals(new BigDecimal("0.101"), quote.get("shortageCardHours"));
        assertEquals(new BigDecimal("0.200"), quote.get("purchaseCardHours"));
        assertEquals(new BigDecimal("0.2004"), quote.get("cnyCost"));
        assertTrue((Boolean) quote.get("canAutoTopUp"));
    }

    @Test
    void reportsTheExactCnyShortfallWithoutChargingAnything() {
        Map<String, Object> quote = ComputeCenterService.buildCardHourTopUpQuote(
                new BigDecimal("24.000"), new BigDecimal("6.900"),
                new BigDecimal("1.0020"), new BigDecimal("10.0000"));

        assertEquals(new BigDecimal("17.100"), quote.get("purchaseCardHours"));
        assertEquals(new BigDecimal("17.1342"), quote.get("cnyCost"));
        assertEquals(new BigDecimal("7.1342"), quote.get("cnyShortfall"));
        assertFalse((Boolean) quote.get("canAutoTopUp"));
    }

    @Test
    void producesNoTopUpWhenCardHoursAreAlreadyEnough() {
        Map<String, Object> quote = ComputeCenterService.buildCardHourTopUpQuote(
                new BigDecimal("1.000"), new BigDecimal("2.000"),
                new BigDecimal("1.0020"), BigDecimal.ZERO);

        assertEquals(new BigDecimal("0.000"), quote.get("purchaseCardHours"));
        assertEquals(new BigDecimal("0.0000"), quote.get("cnyCost"));
        assertTrue((Boolean) quote.get("canAutoTopUp"));
    }
}
