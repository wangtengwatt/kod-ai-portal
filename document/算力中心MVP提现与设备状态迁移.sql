-- KOD 算力中心 MVP 增量迁移（MySQL 8+）
-- 适用：正式环境 KOD_COMPUTE_SCHEMA_INIT_ENABLED=false 时，由数据库管理员执行一次。
-- 先备份并在测试库验证；开发环境已由 ComputeSchemaInitializer 自动完成，不要重复执行 ALTER。

INSERT IGNORE INTO compute_setting(setting_key, setting_value, description)
VALUES ('card_hour_redeem_rate', '1.0000', '1 卡时兑换到 KOD 人民币钱包的固定金额');

CREATE TABLE IF NOT EXISTS compute_withdrawal (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    withdrawal_no       VARCHAR(64)    NOT NULL,
    request_id          VARCHAR(96)    NOT NULL,
    user_id             BIGINT         NOT NULL,
    card_hours          DECIMAL(20,3)  NOT NULL,
    redeem_rate         DECIMAL(12,4)  NOT NULL,
    cny_amount          DECIMAL(12,4)  NOT NULL,
    status              VARCHAR(24)    NOT NULL DEFAULT 'PENDING',
    destination_type    VARCHAR(32)    NOT NULL DEFAULT 'KOD_CNY_WALLET',
    card_hours_before   DECIMAL(20,3)  NOT NULL,
    card_hours_after    DECIMAL(20,3)  NULL,
    cny_balance_before  DECIMAL(12,4)  NOT NULL,
    cny_balance_after   DECIMAL(12,4)  NULL,
    failure_reason      VARCHAR(512)   NOT NULL DEFAULT '',
    completed_at        DATETIME       NULL,
    create_time         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_compute_withdrawal_no (withdrawal_no),
    UNIQUE KEY uk_compute_withdrawal_request (user_id, request_id),
    INDEX idx_compute_withdrawal_user_time (user_id, create_time),
    INDEX idx_compute_withdrawal_status (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='卡时直接兑换到 KOD 内部人民币钱包的提现记录';

ALTER TABLE compute_reservation
    ADD COLUMN status_before_incident VARCHAR(24) NULL AFTER status,
    ADD COLUMN incident_reason VARCHAR(512) NOT NULL DEFAULT '' AFTER status_before_incident,
    ADD COLUMN resolution_type VARCHAR(24) NULL AFTER incident_reason,
    ADD COLUMN resolution_card_hours DECIMAL(20,3) NULL AFTER resolution_type,
    ADD COLUMN resolved_by BIGINT NULL AFTER resolution_card_hours,
    ADD COLUMN resolved_at DATETIME NULL AFTER resolved_by;

-- 旧版“验机通过”拆分为部署中/运行中；仅内测节点直接恢复运行，正式节点等待管理员确认。
UPDATE compute_gpu_node
SET status = CASE WHEN is_test = 1 THEN 'RUNNING' ELSE 'DEPLOYING' END
WHERE status = 'APPROVED';

UPDATE compute_product p
JOIN compute_gpu_node n ON n.id = p.node_id
SET p.status = 'PAUSED'
WHERE p.product_type = 'GPU' AND p.status = 'PUBLISHED' AND n.status <> 'RUNNING';

-- 验收查询
SELECT setting_key, setting_value FROM compute_setting WHERE setting_key = 'card_hour_redeem_rate';
SHOW CREATE TABLE compute_withdrawal;
SELECT status, COUNT(*) AS node_count FROM compute_gpu_node GROUP BY status;
