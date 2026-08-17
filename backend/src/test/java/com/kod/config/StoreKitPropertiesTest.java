package com.kod.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StoreKitPropertiesTest {
    @Test
    void parsesContainerFriendlyConsumableCreditMappings() {
        StoreKitProperties properties = new StoreKitProperties();
        properties.setConsumableCreditsSpec("com.kai.kod.credits.100=100, com.kai.kod.credits.500=500");

        assertEquals(100L, properties.getConsumableCredits().get("com.kai.kod.credits.100"));
        assertEquals(500L, properties.getConsumableCredits().get("com.kai.kod.credits.500"));
    }

    @Test
    void rejectsNonPositiveCreditGrants() {
        StoreKitProperties properties = new StoreKitProperties();
        properties.setConsumableCreditsSpec("com.kai.kod.credits.bad=0");

        assertThrows(IllegalStateException.class, properties::getConsumableCredits);
    }
}
