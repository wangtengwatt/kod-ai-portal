package com.kod.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MediaObjectSchemaInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final MediaStorageProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isSchemaInitEnabled()) return;
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS kod_media_object (
                  user_id BIGINT NOT NULL,
                  storage_key VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
                  object_key VARCHAR(191) NOT NULL,
                  content_type VARCHAR(127) NOT NULL,
                  byte_size BIGINT NOT NULL,
                  status VARCHAR(24) NOT NULL DEFAULT 'active',
                  created_at BIGINT NOT NULL,
                  updated_at BIGINT NOT NULL,
                  PRIMARY KEY (user_id, storage_key),
                  UNIQUE KEY uk_kod_media_object_key (object_key),
                  INDEX idx_kod_media_cleanup (status, updated_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        log.info("Cross-device media schema is ready");
    }
}
