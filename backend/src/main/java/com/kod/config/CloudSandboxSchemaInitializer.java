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
public class CloudSandboxSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final CloudSandboxProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isSchemaInitEnabled()) return;
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS kod_worker_pair_code (
                  code_hash CHAR(64) NOT NULL,
                  expires_at BIGINT NOT NULL,
                  used_at BIGINT NULL,
                  created_at BIGINT NOT NULL,
                  PRIMARY KEY (code_hash)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS kod_worker_node (
                  id VARCHAR(36) NOT NULL,
                  name VARCHAR(128) NOT NULL,
                  token_hash CHAR(64) NOT NULL,
                  capabilities_json TEXT NOT NULL,
                  status VARCHAR(16) NOT NULL,
                  last_seen_at BIGINT NOT NULL,
                  created_at BIGINT NOT NULL,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_kod_worker_token (token_hash),
                  INDEX idx_kod_worker_online (status, last_seen_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS kod_cloud_workspace (
                  id VARCHAR(36) NOT NULL,
                  user_id BIGINT NOT NULL,
                  working_directory VARCHAR(512) NOT NULL,
                  state VARCHAR(24) NOT NULL,
                  worker_id VARCHAR(36) NULL,
                  created_at BIGINT NOT NULL,
                  updated_at BIGINT NOT NULL,
                  PRIMARY KEY (id),
                  INDEX idx_kod_workspace_owner (user_id, updated_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS kod_task_operation (
                  id VARCHAR(36) NOT NULL,
                  workspace_id VARCHAR(36) NOT NULL,
                  user_id BIGINT NOT NULL,
                  kind VARCHAR(16) NOT NULL,
                  params_json LONGTEXT NOT NULL,
                  state VARCHAR(24) NOT NULL,
                  result_json LONGTEXT NULL,
                  error_message TEXT NULL,
                  worker_id VARCHAR(36) NULL,
                  cancel_requested BOOLEAN NOT NULL DEFAULT FALSE,
                  created_at BIGINT NOT NULL,
                  started_at BIGINT NULL,
                  finished_at BIGINT NULL,
                  PRIMARY KEY (id),
                  INDEX idx_kod_operation_queue (state, created_at),
                  INDEX idx_kod_operation_owner (user_id, workspace_id, created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS kod_task_event (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  user_id BIGINT NOT NULL,
                  workspace_id VARCHAR(36) NOT NULL,
                  operation_id VARCHAR(36) NULL,
                  event_type VARCHAR(32) NOT NULL,
                  payload_json LONGTEXT NULL,
                  created_at BIGINT NOT NULL,
                  PRIMARY KEY (id),
                  INDEX idx_kod_task_event_stream (user_id, workspace_id, id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS kod_audit_event (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  user_id BIGINT NULL,
                  actor_type VARCHAR(24) NOT NULL,
                  actor_id VARCHAR(64) NOT NULL,
                  action_name VARCHAR(64) NOT NULL,
                  target_type VARCHAR(32) NOT NULL,
                  target_id VARCHAR(64) NOT NULL,
                  metadata_json TEXT NULL,
                  created_at BIGINT NOT NULL,
                  PRIMARY KEY (id),
                  INDEX idx_kod_audit_user (user_id, created_at),
                  INDEX idx_kod_audit_target (target_type, target_id, created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        log.info("Cloud sandbox schema is ready");
    }
}
