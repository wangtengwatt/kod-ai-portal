package com.kod.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Private S3-compatible storage used by cross-device media synchronization. */
@Data
@Component
@ConfigurationProperties(prefix = "kod.media-storage")
public class MediaStorageProperties {
    private boolean enabled = false;
    private boolean schemaInitEnabled = true;
    private String endpoint = "";
    private String region = "us-east-1";
    private String bucket = "";
    private String accessKey = "";
    private String secretKey = "";
    private boolean pathStyleAccess = false;
    private String serverSideEncryption = "AES256";
    private long maxObjectBytes = 128L * 1024L * 1024L;
    private long maxUserBytes = 5L * 1024L * 1024L * 1024L;
    private long cleanupIntervalMillis = 300_000L;
}
