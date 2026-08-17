-- KOD 第三方 GPU 行情独立迁移（MySQL 8+）
-- 只新增 compute_* 表和配置，不修改同事维护的人民币钱包或存量业务表。

INSERT IGNORE INTO compute_setting(setting_key, setting_value, description) VALUES
    ('usd_cny_rate', '7.2000', '第三方 GPU 行情美元兑人民币估算汇率');

CREATE TABLE IF NOT EXISTS compute_market_price_history (
    id                      BIGINT         NOT NULL AUTO_INCREMENT,
    source                  VARCHAR(24)    NOT NULL,
    gpu_model               VARCHAR(96)    NOT NULL,
    quote_type              VARCHAR(24)    NOT NULL,
    price_usd_per_gpu_hour  DECIMAL(16,6)  NOT NULL,
    sample_size             INT            NOT NULL DEFAULT 0,
    sampled_at              DATETIME       NOT NULL,
    create_time             DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_compute_market_price_sample (source, gpu_model, sampled_at),
    INDEX idx_compute_market_price_history (gpu_model, sampled_at),
    INDEX idx_compute_market_price_cleanup (sampled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='第三方 GPU 行情分钟级历史采样';
