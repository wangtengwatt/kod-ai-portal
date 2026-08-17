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
public class KnowledgeBaseSchemaInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final KnowledgeBaseProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isSchemaInitEnabled()) return;
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS kod_knowledge_base (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  user_id BIGINT NOT NULL,
                  name VARCHAR(255) NOT NULL,
                  embedding_model VARCHAR(255) NOT NULL,
                  rerank_model VARCHAR(255) NOT NULL,
                  vision_model VARCHAR(255) NULL,
                  provider_mode VARCHAR(32) NULL,
                  document_parser_json TEXT NULL,
                  created_at BIGINT NOT NULL,
                  updated_at BIGINT NOT NULL,
                  PRIMARY KEY (id),
                  INDEX idx_kod_kb_owner (user_id, created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS kod_knowledge_base_file (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  kb_id BIGINT NOT NULL,
                  user_id BIGINT NOT NULL,
                  filename VARCHAR(512) NOT NULL,
                  mime_type VARCHAR(255) NOT NULL,
                  file_size BIGINT NOT NULL,
                  raw_data LONGBLOB NOT NULL,
                  chunk_count INT NOT NULL DEFAULT 0,
                  total_chunks INT NOT NULL DEFAULT 0,
                  status VARCHAR(24) NOT NULL,
                  error_message TEXT NULL,
                  parser_type VARCHAR(32) NULL,
                  created_at BIGINT NOT NULL,
                  updated_at BIGINT NOT NULL,
                  PRIMARY KEY (id),
                  INDEX idx_kod_kb_file_owner (user_id, kb_id, created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS kod_knowledge_base_chunk (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  kb_id BIGINT NOT NULL,
                  file_id BIGINT NOT NULL,
                  user_id BIGINT NOT NULL,
                  chunk_index INT NOT NULL,
                  content MEDIUMTEXT NOT NULL,
                  created_at BIGINT NOT NULL,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_kod_kb_chunk (file_id, chunk_index),
                  INDEX idx_kod_kb_chunk_search (user_id, kb_id, file_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        log.info("Cloud knowledge base schema is ready");
    }
}
