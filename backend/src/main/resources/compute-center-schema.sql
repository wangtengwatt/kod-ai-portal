-- KOD 算力中心 MVP 独立表结构（MySQL 8+）
--
-- 设计约束：
-- 1. 不修改 sys_user、orders、api_request_logs 等任何存量表。
-- 2. 所有新表统一使用 compute_ 前缀，可独立部署和回滚。
-- 3. 金额继续使用存量钱包的 4 位小数；卡时统一使用 3 位小数。
-- 4. 本脚本只建表，不包含 DROP、ALTER 或生产种子数据，可重复执行。

CREATE TABLE IF NOT EXISTS compute_setting (
    setting_key     VARCHAR(64)    NOT NULL,
    setting_value   VARCHAR(512)   NOT NULL,
    description     VARCHAR(512)   NOT NULL DEFAULT '',
    update_user_id  BIGINT         NULL,
    update_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='算力中心动态配置';

INSERT IGNORE INTO compute_setting(setting_key, setting_value, description) VALUES
    ('card_hour_cny_rate', '1.002', '1 KAI 标准卡时对应人民币金额'),
    ('card_hour_redeem_rate', '1.0000', '1 卡时兑换到 KOD 人民币钱包的固定金额'),
    ('transfer_review_threshold', '1000.000', '转让达到该卡时数量时需要管理员审核'),
    ('platform_fee_rate', '0', '平台服务费比例，内部 MVP 默认 0');

CREATE TABLE IF NOT EXISTS compute_account (
    user_id                 BIGINT         NOT NULL,
    available_card_hours    DECIMAL(20,3)  NOT NULL DEFAULT 0.000,
    frozen_card_hours       DECIMAL(20,3)  NOT NULL DEFAULT 0.000,
    lifetime_income         DECIMAL(20,3)  NOT NULL DEFAULT 0.000,
    lifetime_consumption    DECIMAL(20,3)  NOT NULL DEFAULT 0.000,
    version                 BIGINT         NOT NULL DEFAULT 0,
    create_time             DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time             DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户卡时账户汇总';

CREATE TABLE IF NOT EXISTS compute_card_hour_lot (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    owner_user_id       BIGINT         NOT NULL,
    source_type         VARCHAR(32)    NOT NULL,
    source_ref          VARCHAR(128)   NOT NULL DEFAULT '',
    original_amount     DECIMAL(20,3)  NOT NULL,
    remaining_amount    DECIMAL(20,3)  NOT NULL,
    frozen_amount       DECIMAL(20,3)  NOT NULL DEFAULT 0.000,
    expires_at          DATETIME       NULL,
    create_time         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_compute_lot_owner_expiry (owner_user_id, expires_at),
    INDEX idx_compute_lot_source (source_type, source_ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='可追踪有效期的卡时批次';

CREATE TABLE IF NOT EXISTS compute_ledger (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    user_id             BIGINT         NOT NULL,
    entry_type          VARCHAR(32)    NOT NULL,
    direction           VARCHAR(8)     NOT NULL,
    amount              DECIMAL(20,3)  NOT NULL,
    available_after     DECIMAL(20,3)  NOT NULL,
    frozen_after        DECIMAL(20,3)  NOT NULL,
    reference_type      VARCHAR(32)    NOT NULL DEFAULT '',
    reference_id        VARCHAR(128)   NOT NULL DEFAULT '',
    description         VARCHAR(512)   NOT NULL DEFAULT '',
    operator_user_id    BIGINT         NULL,
    idempotency_key     VARCHAR(160)   NULL,
    create_time         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_compute_ledger_idempotency (idempotency_key),
    INDEX idx_compute_ledger_user_time (user_id, create_time),
    INDEX idx_compute_ledger_reference (reference_type, reference_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可修改的卡时流水';

CREATE TABLE IF NOT EXISTS compute_supplier (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    user_id             BIGINT         NOT NULL,
    display_name        VARCHAR(128)   NOT NULL,
    contact             VARCHAR(256)   NOT NULL DEFAULT '',
    description         VARCHAR(1000)  NOT NULL DEFAULT '',
    status              VARCHAR(24)    NOT NULL DEFAULT 'PENDING',
    rejection_reason    VARCHAR(512)   NOT NULL DEFAULT '',
    reviewed_by         BIGINT         NULL,
    reviewed_at         DATETIME       NULL,
    create_time         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_compute_supplier_user (user_id),
    INDEX idx_compute_supplier_status (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='算力供应方申请与审核';

CREATE TABLE IF NOT EXISTS compute_product (
    id                              BIGINT         NOT NULL AUTO_INCREMENT,
    supplier_user_id                BIGINT         NULL,
    node_id                         BIGINT         NULL,
    product_type                    VARCHAR(16)    NOT NULL,
    name                            VARCHAR(256)   NOT NULL,
    description                     VARCHAR(2000)  NOT NULL DEFAULT '',
    region                          VARCHAR(128)   NOT NULL DEFAULT '',
    status                          VARCHAR(24)    NOT NULL DEFAULT 'DRAFT',
    model_id                        VARCHAR(128)   NULL,
    prompt_rate_per_million         DECIMAL(20,6)  NULL,
    completion_rate_per_million     DECIMAL(20,6)  NULL,
    package_prompt_tokens           BIGINT         NULL,
    package_completion_tokens       BIGINT         NULL,
    package_price_card_hours        DECIMAL(20,3)  NULL,
    upstream_station_id             BIGINT         NULL,
    upstream_key_id                 BIGINT         NULL,
    gpu_model                       VARCHAR(128)   NULL,
    gpu_memory_gb                   INT            NULL,
    gpu_count                       INT            NULL,
    price_per_gpu_hour              DECIMAL(20,3)  NULL,
    trade_mode                      VARCHAR(32)    NOT NULL DEFAULT 'LEGACY_RESERVATION',
    package_duration_hours          INT            NULL,
    delivery_deadline_hours         INT            NULL,
    available_from                  DATETIME       NULL,
    available_to                    DATETIME       NULL,
    delivery_mode                   VARCHAR(64)    NOT NULL DEFAULT '',
    sla_description                 VARCHAR(512)   NOT NULL DEFAULT '',
    is_test                         TINYINT        NOT NULL DEFAULT 0,
    rejection_reason                VARCHAR(512)   NOT NULL DEFAULT '',
    reviewed_by                     BIGINT         NULL,
    reviewed_at                     DATETIME       NULL,
    published_at                    DATETIME       NULL,
    create_time                     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time                     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_compute_product_market (status, product_type, create_time),
    INDEX idx_compute_product_supplier (supplier_user_id, create_time),
    INDEX idx_compute_product_node (node_id, create_time),
    INDEX idx_compute_product_model (model_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型 API 与 GPU 算力商品';

CREATE TABLE IF NOT EXISTS compute_product_image (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    product_id          BIGINT         NOT NULL,
    file_id             VARCHAR(160)   NOT NULL,
    mime_type           VARCHAR(64)    NOT NULL,
    sort_order          INT            NOT NULL DEFAULT 0,
    create_time         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_compute_product_image_product (product_id, sort_order, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GPU 商品封面与详情图片';

CREATE TABLE IF NOT EXISTS compute_product_activation (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    user_id             BIGINT       NOT NULL,
    product_id          BIGINT       NOT NULL,
    status              VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_compute_activation_user_product (user_id, product_id),
    INDEX idx_compute_activation_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户已开通的模型 API 商品';

CREATE TABLE IF NOT EXISTS compute_order (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    order_no            VARCHAR(64)    NOT NULL,
    user_id             BIGINT         NOT NULL,
    order_type          VARCHAR(24)    NOT NULL,
    product_id          BIGINT         NULL,
    card_hours          DECIMAL(20,3)  NOT NULL DEFAULT 0.000,
    cny_amount          DECIMAL(12,4)  NOT NULL DEFAULT 0.0000,
    status              VARCHAR(24)    NOT NULL,
    snapshot_json       JSON           NULL,
    create_time         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_compute_order_no (order_no),
    INDEX idx_compute_order_user_time (user_id, create_time),
    INDEX idx_compute_order_status (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='卡时购买、API 开通与 GPU 预订订单';

CREATE TABLE IF NOT EXISTS compute_reservation (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    order_id            BIGINT         NOT NULL,
    product_id          BIGINT         NOT NULL,
    buyer_user_id       BIGINT         NOT NULL,
    supplier_user_id    BIGINT         NULL,
    gpu_count           INT            NOT NULL,
    start_time          DATETIME       NOT NULL,
    end_time            DATETIME       NOT NULL,
    unit_rate_snapshot  DECIMAL(20,3)  NOT NULL,
    frozen_card_hours   DECIMAL(20,3)  NOT NULL,
    settled_card_hours  DECIMAL(20,3)  NOT NULL DEFAULT 0.000,
    platform_fee        DECIMAL(20,3)  NOT NULL DEFAULT 0.000,
    status              VARCHAR(24)    NOT NULL DEFAULT 'PENDING_DELIVERY',
    status_before_incident VARCHAR(24) NULL,
    incident_reason     VARCHAR(512)   NOT NULL DEFAULT '',
    resolution_type     VARCHAR(24)    NULL,
    resolution_card_hours DECIMAL(20,3) NULL,
    resolved_by         BIGINT         NULL,
    resolved_at         DATETIME       NULL,
    delivery_ciphertext MEDIUMTEXT     NULL,
    trade_mode          VARCHAR(32)    NOT NULL DEFAULT 'LEGACY_RESERVATION',
    buyer_public_key    MEDIUMTEXT     NULL,
    delivery_deadline_at DATETIME      NULL,
    auto_confirm_at     DATETIME       NULL,
    buyer_confirmed_at  DATETIME       NULL,
    dispute_reason      VARCHAR(1000)  NOT NULL DEFAULT '',
    dispute_evidence    VARCHAR(2000)  NOT NULL DEFAULT '',
    disputed_at         DATETIME       NULL,
    delivered_at        DATETIME       NULL,
    cancelled_at        DATETIME       NULL,
    completed_at        DATETIME       NULL,
    create_time         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_compute_reservation_order (order_id),
    INDEX idx_compute_reservation_buyer (buyer_user_id, create_time),
    INDEX idx_compute_reservation_supplier (supplier_user_id, create_time),
    INDEX idx_compute_reservation_schedule (status, start_time, end_time),
    INDEX idx_compute_reservation_product_slot (product_id, start_time, end_time),
    INDEX idx_compute_reservation_marketplace (trade_mode, status, delivery_deadline_at, auto_confirm_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GPU 历史预订与固定套餐担保交易';

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

CREATE TABLE IF NOT EXISTS compute_transfer (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    transfer_no         VARCHAR(64)    NOT NULL,
    sender_user_id      BIGINT         NOT NULL,
    recipient_user_id   BIGINT         NOT NULL,
    amount              DECIMAL(20,3)  NOT NULL,
    message             VARCHAR(512)   NOT NULL DEFAULT '',
    status              VARCHAR(32)    NOT NULL,
    review_reason       VARCHAR(512)   NOT NULL DEFAULT '',
    reviewed_by         BIGINT         NULL,
    reviewed_at         DATETIME       NULL,
    accepted_at         DATETIME       NULL,
    expires_at          DATETIME       NOT NULL,
    create_time         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_compute_transfer_no (transfer_no),
    INDEX idx_compute_transfer_sender (sender_user_id, create_time),
    INDEX idx_compute_transfer_recipient (recipient_user_id, status, create_time),
    INDEX idx_compute_transfer_status (status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户间无偿卡时转让';

CREATE TABLE IF NOT EXISTS compute_freeze_allocation (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    purpose_type        VARCHAR(24)    NOT NULL,
    purpose_id          BIGINT         NOT NULL,
    lot_id              BIGINT         NOT NULL,
    amount              DECIMAL(20,3)  NOT NULL,
    expires_at          DATETIME       NULL,
    create_time         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_compute_freeze_purpose_lot (purpose_type, purpose_id, lot_id),
    INDEX idx_compute_freeze_lot (lot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预订和转让冻结金额的批次分配';

CREATE TABLE IF NOT EXISTS compute_notification (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    user_id             BIGINT         NOT NULL,
    notification_type   VARCHAR(32)    NOT NULL,
    title               VARCHAR(256)   NOT NULL,
    content             VARCHAR(1000)  NOT NULL DEFAULT '',
    reference_type      VARCHAR(32)    NOT NULL DEFAULT '',
    reference_id        VARCHAR(128)   NOT NULL DEFAULT '',
    is_read             TINYINT        NOT NULL DEFAULT 0,
    create_time         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_time           DATETIME       NULL,
    PRIMARY KEY (id),
    INDEX idx_compute_notification_user (user_id, is_read, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='KOD 客户端内通知';

CREATE TABLE IF NOT EXISTS compute_audit_log (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    operator_user_id    BIGINT        NOT NULL,
    action              VARCHAR(64)   NOT NULL,
    target_type         VARCHAR(32)   NOT NULL,
    target_id           VARCHAR(128)  NOT NULL,
    detail_json         JSON          NULL,
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_compute_audit_operator (operator_user_id, create_time),
    INDEX idx_compute_audit_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='算力中心管理员审计日志';

CREATE TABLE IF NOT EXISTS compute_api_usage_charge (
    id                      BIGINT         NOT NULL AUTO_INCREMENT,
    api_request_log_id      BIGINT         NOT NULL,
    request_id              VARCHAR(128)   NOT NULL,
    user_id                 BIGINT         NOT NULL,
    product_id              BIGINT         NOT NULL,
    model_id                VARCHAR(128)   NOT NULL,
    prompt_tokens           INT            NOT NULL DEFAULT 0,
    completion_tokens       INT            NOT NULL DEFAULT 0,
    deducted_prompt_tokens  BIGINT         NOT NULL DEFAULT 0,
    deducted_completion_tokens BIGINT      NOT NULL DEFAULT 0,
    gifted_prompt_tokens    BIGINT         NOT NULL DEFAULT 0,
    gifted_completion_tokens BIGINT        NOT NULL DEFAULT 0,
    requested_card_hours    DECIMAL(20,3)  NOT NULL,
    charged_card_hours      DECIMAL(20,3)  NOT NULL,
    unpaid_card_hours       DECIMAL(20,3)  NOT NULL DEFAULT 0.000,
    status                  VARCHAR(16)    NOT NULL,
    create_time             DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_compute_api_charge_log (api_request_log_id),
    UNIQUE KEY uk_compute_api_charge_request (request_id),
    INDEX idx_compute_api_charge_user (user_id, create_time),
    INDEX idx_compute_api_charge_product (product_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型 API 卡时扣费及欠费记录';

CREATE TABLE IF NOT EXISTS compute_api_package_purchase (
    id                          BIGINT         NOT NULL AUTO_INCREMENT,
    order_id                    BIGINT         NOT NULL,
    user_id                     BIGINT         NOT NULL,
    product_id                  BIGINT         NOT NULL,
    model_id                    VARCHAR(128)   NOT NULL,
    prompt_tokens_total         BIGINT         NOT NULL,
    prompt_tokens_remaining     BIGINT         NOT NULL,
    completion_tokens_total     BIGINT         NOT NULL,
    completion_tokens_remaining BIGINT         NOT NULL,
    price_card_hours            DECIMAL(20,3)  NOT NULL,
    status                      VARCHAR(24)    NOT NULL DEFAULT 'ACTIVE',
    access_key_hash             CHAR(64)       NULL,
    access_key_ciphertext       TEXT           NULL,
    access_key_last4            VARCHAR(8)     NOT NULL DEFAULT '',
    key_status                  VARCHAR(24)    NOT NULL DEFAULT 'CONFIG_REQUIRED',
    suspended_reason            VARCHAR(512)   NOT NULL DEFAULT '',
    in_flight                   TINYINT        NOT NULL DEFAULT 0,
    create_time                 DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time                 DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_compute_api_package_order (order_id),
    UNIQUE KEY uk_compute_api_package_access_key (access_key_hash),
    INDEX idx_compute_api_package_user_model (user_id, model_id, status, id),
    INDEX idx_compute_api_package_product (product_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户购买的永久模型 Token 套餐及 FIFO 余额';

CREATE TABLE IF NOT EXISTS compute_api_proxy_request (
    id                          BIGINT         NOT NULL AUTO_INCREMENT,
    request_id                  VARCHAR(128)   NOT NULL,
    package_purchase_id         BIGINT         NOT NULL,
    user_id                     BIGINT         NOT NULL,
    product_id                  BIGINT         NOT NULL,
    endpoint                    VARCHAR(48)    NOT NULL,
    model_id                    VARCHAR(128)   NOT NULL,
    prompt_tokens               BIGINT         NOT NULL DEFAULT 0,
    completion_tokens           BIGINT         NOT NULL DEFAULT 0,
    deducted_prompt_tokens      BIGINT         NOT NULL DEFAULT 0,
    deducted_completion_tokens  BIGINT         NOT NULL DEFAULT 0,
    gifted_prompt_tokens        BIGINT         NOT NULL DEFAULT 0,
    gifted_completion_tokens    BIGINT         NOT NULL DEFAULT 0,
    upstream_status             INT            NULL,
    status                      VARCHAR(24)    NOT NULL DEFAULT 'IN_FLIGHT',
    error_message               VARCHAR(1000)  NOT NULL DEFAULT '',
    completed_at                DATETIME       NULL,
    create_time                 DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_compute_proxy_request_id (request_id),
    INDEX idx_compute_proxy_package_time (package_purchase_id, create_time),
    INDEX idx_compute_proxy_user_time (user_id, create_time),
    INDEX idx_compute_proxy_status_time (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Token 套餐平台代理调用、Token 扣减与异常流水';

CREATE TABLE IF NOT EXISTS compute_identity_verification (
    id                      BIGINT         NOT NULL AUTO_INCREMENT,
    user_id                 BIGINT         NOT NULL,
    verification_type       VARCHAR(16)    NOT NULL DEFAULT 'REAL',
    real_name_ciphertext    TEXT           NULL,
    identity_no_ciphertext  TEXT           NULL,
    identity_fingerprint    CHAR(64)       NOT NULL,
    identity_no_masked      VARCHAR(32)    NOT NULL,
    front_file_id           VARCHAR(160)   NULL,
    back_file_id            VARCHAR(160)   NULL,
    status                  VARCHAR(24)    NOT NULL DEFAULT 'PENDING',
    rejection_reason        VARCHAR(512)   NOT NULL DEFAULT '',
    reviewed_by             BIGINT         NULL,
    reviewed_at             DATETIME       NULL,
    closed_at               DATETIME       NULL,
    purge_after             DATETIME       NULL,
    purged_at               DATETIME       NULL,
    create_time             DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time             DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_compute_identity_user (user_id, status, create_time),
    INDEX idx_compute_identity_fingerprint (identity_fingerprint, status),
    INDEX idx_compute_identity_purge (purge_after, purged_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应方实名认证申请；敏感字段及证件文件均加密';

CREATE TABLE IF NOT EXISTS compute_referral_profile (
    user_id                 BIGINT         NOT NULL,
    invite_code             CHAR(32)       NOT NULL,
    create_time             DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time             DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_compute_referral_invite_code (invite_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='算力中心独立邀请链接，不与登录邀请码关联';

CREATE TABLE IF NOT EXISTS compute_referral_binding (
    id                      BIGINT         NOT NULL AUTO_INCREMENT,
    inviter_user_id         BIGINT         NOT NULL,
    invitee_user_id         BIGINT         NOT NULL,
    invite_code             CHAR(32)       NOT NULL,
    device_hash             CHAR(64)       NOT NULL,
    status                  VARCHAR(16)    NOT NULL DEFAULT 'ACTIVE',
    bound_at                DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_time             DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_compute_referral_invitee (invitee_user_id),
    UNIQUE KEY uk_compute_referral_device (device_hash),
    INDEX idx_compute_referral_inviter (inviter_user_id, status, bound_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='算力中心邀请关系，一名用户和一台设备只允许绑定一次';

CREATE TABLE IF NOT EXISTS compute_referral_reward (
    id                      BIGINT         NOT NULL AUTO_INCREMENT,
    binding_id              BIGINT         NOT NULL,
    inviter_user_id         BIGINT         NOT NULL,
    invitee_user_id         BIGINT         NOT NULL,
    topup_order_id          BIGINT         NOT NULL,
    topup_order_no          VARCHAR(128)   NOT NULL,
    recharge_amount         DECIMAL(12,4)  NOT NULL,
    reward_rate             DECIMAL(8,6)   NOT NULL DEFAULT 0.050000,
    reward_cap              DECIMAL(12,4)  NOT NULL DEFAULT 100.0000,
    reward_amount           DECIMAL(12,4)  NOT NULL,
    status                  VARCHAR(16)    NOT NULL DEFAULT 'WAITING',
    release_at              DATETIME       NOT NULL,
    paid_at                 DATETIME       NULL,
    cancelled_at            DATETIME       NULL,
    cancel_reason           VARCHAR(512)   NOT NULL DEFAULT '',
    create_time             DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time             DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_compute_referral_reward_invitee (invitee_user_id),
    UNIQUE KEY uk_compute_referral_reward_order (topup_order_id),
    INDEX idx_compute_referral_reward_inviter (inviter_user_id, status, create_time),
    INDEX idx_compute_referral_reward_release (status, release_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='首次充值邀请返佣，等待七天后自动发放';

CREATE TABLE IF NOT EXISTS compute_gpu_node (
    id                      BIGINT         NOT NULL AUTO_INCREMENT,
    supplier_user_id        BIGINT         NOT NULL,
    identity_verification_id BIGINT        NOT NULL,
    node_name               VARCHAR(128)   NOT NULL,
    region                  VARCHAR(128)   NOT NULL DEFAULT '',
    gpu_model               VARCHAR(128)   NOT NULL,
    gpu_memory_gb           INT            NOT NULL,
    gpu_count               INT            NOT NULL,
    cpu_description         VARCHAR(256)   NOT NULL DEFAULT '',
    ram_gb                  INT            NOT NULL DEFAULT 0,
    storage_gb              INT            NOT NULL DEFAULT 0,
    network_description     VARCHAR(256)   NOT NULL DEFAULT '',
    proof_file_id           VARCHAR(160)   NULL,
    proof_mime_type         VARCHAR(64)    NULL,
    ssh_host_ciphertext     TEXT           NOT NULL,
    ssh_port                INT            NOT NULL DEFAULT 22,
    ssh_username_ciphertext TEXT           NOT NULL,
    ssh_auth_type           VARCHAR(16)    NOT NULL,
    ssh_credential_ciphertext MEDIUMTEXT   NOT NULL,
    status                  VARCHAR(24)    NOT NULL DEFAULT 'PENDING',
    is_test                 TINYINT        NOT NULL DEFAULT 0,
    review_reason           VARCHAR(512)   NOT NULL DEFAULT '',
    verification_note       VARCHAR(1000)  NOT NULL DEFAULT '',
    reviewed_by             BIGINT         NULL,
    reviewed_at             DATETIME       NULL,
    create_time             DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time             DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_compute_gpu_node_supplier (supplier_user_id, status, create_time),
    INDEX idx_compute_gpu_node_review (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应方托管 GPU 节点和人工验机记录';
