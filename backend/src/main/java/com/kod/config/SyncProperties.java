package com.kod.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Cross-device synchronization and encrypted vault configuration. */
@Data
@Component
@ConfigurationProperties(prefix = "kod.sync")
public class SyncProperties {

    private boolean enabled = true;
    private boolean schemaInitEnabled = true;
    /** Base64-encoded 256-bit KEK. Sensitive sync is rejected while this is empty. */
    private String vaultMasterKey = "";
}
