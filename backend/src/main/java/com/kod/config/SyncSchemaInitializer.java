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
public class SyncSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final SyncProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isSchemaInitEnabled()) return;
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS kod_secret_vault (
                  id VARCHAR(36) NOT NULL,
                  user_id BIGINT NOT NULL,
                  encrypted_dek LONGBLOB NOT NULL,
                  dek_iv VARBINARY(12) NOT NULL,
                  ciphertext LONGBLOB NOT NULL,
                  payload_iv VARBINARY(12) NOT NULL,
                  created_at BIGINT NOT NULL,
                  PRIMARY KEY (id),
                  INDEX idx_kod_secret_owner (user_id, created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS kod_sync_record (
                  user_id BIGINT NOT NULL,
                  entity_type VARCHAR(32) NOT NULL,
                  entity_id VARCHAR(191) NOT NULL,
                  revision BIGINT NOT NULL,
                  client_updated_at BIGINT NOT NULL,
                  server_updated_at BIGINT NOT NULL,
                  source_device_id VARCHAR(64) NOT NULL,
                  deleted BOOLEAN NOT NULL DEFAULT FALSE,
                  is_sensitive BOOLEAN NOT NULL DEFAULT FALSE,
                  payload_json LONGTEXT NULL,
                  secret_id VARCHAR(36) NULL,
                  PRIMARY KEY (user_id, entity_type, entity_id),
                  INDEX idx_kod_sync_record_updated (user_id, server_updated_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS kod_sync_event (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  user_id BIGINT NOT NULL,
                  entity_type VARCHAR(32) NOT NULL,
                  entity_id VARCHAR(191) NOT NULL,
                  revision BIGINT NOT NULL,
                  client_updated_at BIGINT NOT NULL,
                  server_updated_at BIGINT NOT NULL,
                  source_device_id VARCHAR(64) NOT NULL,
                  deleted BOOLEAN NOT NULL DEFAULT FALSE,
                  is_sensitive BOOLEAN NOT NULL DEFAULT FALSE,
                  payload_json LONGTEXT NULL,
                  secret_id VARCHAR(36) NULL,
                  PRIMARY KEY (id),
                  INDEX idx_kod_sync_event_pull (user_id, id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS kod_sync_mutation (
                  user_id BIGINT NOT NULL,
                  mutation_id VARCHAR(64) NOT NULL,
                  event_cursor BIGINT NOT NULL,
                  created_at BIGINT NOT NULL,
                  PRIMARY KEY (user_id, mutation_id),
                  INDEX idx_kod_sync_mutation_created (created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        log.info("Cross-device sync schema is ready");
    }
}
