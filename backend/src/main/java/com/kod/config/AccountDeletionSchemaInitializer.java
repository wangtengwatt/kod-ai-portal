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
public class AccountDeletionSchemaInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS kod_account_deletion_receipt (
                  user_id BIGINT NOT NULL,
                  status VARCHAR(24) NOT NULL,
                  requested_at BIGINT NOT NULL,
                  completed_at BIGINT NULL,
                  PRIMARY KEY (user_id),
                  INDEX idx_kod_account_deletion_status (status, requested_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        log.info("Account-deletion receipt schema is ready");
    }
}
