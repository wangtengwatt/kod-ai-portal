package com.kod.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "kod.knowledge-base")
public class KnowledgeBaseProperties {
    private boolean enabled = true;
    private boolean schemaInitEnabled = true;
    private long maxFileBytes = 32L * 1024 * 1024;
    private int maxExtractedCharacters = 5_000_000;
}
