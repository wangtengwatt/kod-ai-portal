package com.kod.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "kod.cloud-sandbox")
public class CloudSandboxProperties {

    private boolean enabled = false;
    private boolean schemaInitEnabled = true;
    private String workerBootstrapSecret = "";
    private long workerHeartbeatTtlMillis = 60_000;
    private int maxQueuedOperationsPerUser = 20;
}
