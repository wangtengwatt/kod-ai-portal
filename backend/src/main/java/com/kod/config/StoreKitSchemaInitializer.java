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
public class StoreKitSchemaInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final StoreKitProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isSchemaInitEnabled()) return;
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS kod_ios_store_account (
                  user_id BIGINT NOT NULL,
                  app_account_token CHAR(36) NOT NULL,
                  created_at BIGINT NOT NULL,
                  PRIMARY KEY (user_id),
                  UNIQUE KEY uk_kod_ios_account_token (app_account_token)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS kod_ios_store_transaction (
                  transaction_id VARCHAR(64) NOT NULL,
                  original_transaction_id VARCHAR(64) NOT NULL,
                  user_id BIGINT NOT NULL,
                  app_account_token CHAR(36) NOT NULL,
                  product_id VARCHAR(255) NOT NULL,
                  product_type VARCHAR(32) NOT NULL,
                  environment VARCHAR(16) NOT NULL,
                  purchase_at BIGINT NOT NULL,
                  expires_at BIGINT NULL,
                  revocation_at BIGINT NULL,
                  signed_transaction LONGTEXT NOT NULL,
                  created_at BIGINT NOT NULL,
                  PRIMARY KEY (transaction_id),
                  INDEX idx_kod_ios_tx_owner (user_id, purchase_at),
                  INDEX idx_kod_ios_tx_original (original_transaction_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS kod_ios_credit_ledger (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  user_id BIGINT NOT NULL,
                  transaction_id VARCHAR(64) NOT NULL,
                  delta BIGINT NOT NULL,
                  reason VARCHAR(64) NOT NULL,
                  created_at BIGINT NOT NULL,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_kod_ios_credit_transaction_reason (transaction_id, reason),
                  INDEX idx_kod_ios_credit_owner (user_id, id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS kod_ios_store_notification (
                  notification_uuid VARCHAR(64) NOT NULL,
                  notification_type VARCHAR(64) NOT NULL,
                  environment VARCHAR(16) NOT NULL,
                  created_at BIGINT NOT NULL,
                  PRIMARY KEY (notification_uuid)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        log.info("StoreKit ledger schema is ready");
    }
}
