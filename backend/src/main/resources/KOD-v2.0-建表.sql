-- ============================================================
-- KOD v2.0 完整建表脚本（含 user_id 字段已合并）
-- 数据库: MySQL 8+
-- 日期: 2026-08-04
-- 说明: 包含 sys_user、relay_station、relay_station_key、
--       orders、coupons、api_request_logs、dashboard_hourly、
--       dashboard_model_summary、log_sync_state 共 9 张表
-- ============================================================

-- 如果已有旧表需要重建，取消下面注释：
-- DROP TABLE IF EXISTS api_request_logs;
-- DROP TABLE IF EXISTS dashboard_hourly;
-- DROP TABLE IF EXISTS dashboard_model_summary;
-- DROP TABLE IF EXISTS log_sync_state;
-- DROP TABLE IF EXISTS orders;
-- DROP TABLE IF EXISTS coupons;
-- DROP TABLE IF EXISTS relay_station_key;
-- DROP TABLE IF EXISTS relay_station;
-- DROP TABLE IF EXISTS sys_user;


-- ============================================================
-- 1. sys_user — 用户表
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_user (
    id                      BIGINT       NOT NULL COMMENT '主键（雪花算法生成）',
    email                   VARCHAR(128) NOT NULL COMMENT '登录邮箱（唯一）',
    password                VARCHAR(256) NOT NULL COMMENT 'BCrypt加密后的密码',
    station_id              BIGINT       NULL     COMMENT '关联的中转站主键（注册时由邀请码解析）',
    balance                 DECIMAL(12,4) NOT NULL DEFAULT 0.0000 COMMENT '余额（元）',
    historical_consumption  DECIMAL(12,4) NOT NULL DEFAULT 0.0000 COMMENT '历史累计消耗（元）',
    connect                 BIGINT        NULL     COMMENT '当前连接的apikey_id，FK → relay_station_key.id',
    create_time             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_email (email),
    INDEX idx_station_id (station_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';


-- ============================================================
-- 2. relay_station — 中转站表
-- ============================================================
CREATE TABLE IF NOT EXISTS relay_station (
    id          BIGINT       NOT NULL COMMENT '主键（雪花算法生成）',
    url         VARCHAR(512) NOT NULL COMMENT '中转站地址，例如 https://fane.kai.com/v1',
    invite_code VARCHAR(128) NOT NULL COMMENT '邀请码（唯一）',

    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_invite_code (invite_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI中转站表';


-- ============================================================
-- 3. relay_station_key — 中转站API密钥表
-- ============================================================
CREATE TABLE IF NOT EXISTS relay_station_key (
    id          BIGINT       NOT NULL COMMENT '主键（雪花算法生成）',
    station_id  BIGINT       NOT NULL COMMENT '所属中转站主键（关联 relay_station.id）',
    api_key     VARCHAR(512) NOT NULL COMMENT 'API密钥，例如 sk-xxxx',
    status      TINYINT      NOT NULL DEFAULT 0 COMMENT '占用状态：0=空闲(绿点) 1=占用中(红点)',

    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (id),
    INDEX idx_station_id (station_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='中转站API密钥表';


-- ============================================================
-- 4. orders — 订单表
-- ============================================================
CREATE TABLE IF NOT EXISTS orders (
    id               BIGINT         NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    user_id          BIGINT         NOT NULL COMMENT '用户ID',
    product_name     VARCHAR(256)   NOT NULL DEFAULT '' COMMENT '商品名称',
    payment_method   VARCHAR(64)    NOT NULL DEFAULT '' COMMENT '支付方式：alipay/wxpay/stripe/creem',
    payment_provider VARCHAR(32)    NOT NULL DEFAULT 'epay' COMMENT '支付提供商：epay/stripe/creem/waffo/waffo_pancake',
    amount           DECIMAL(12,4)  NOT NULL DEFAULT 0.0000 COMMENT '所需金额（元）',
    actual_payment   DECIMAL(12,4)  NOT NULL DEFAULT 0.0000 COMMENT '实付金额（元）',
    order_no         VARCHAR(128)   NOT NULL DEFAULT '' COMMENT '订单号（唯一）',
    status           VARCHAR(32)    NOT NULL DEFAULT 'pending' COMMENT '订单状态：pending/success/failed/expired',
    coupon_id        BIGINT         NULL COMMENT '优惠券ID',
    create_time      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';


-- ============================================================
-- 5. coupons — 优惠券表
-- ============================================================
CREATE TABLE IF NOT EXISTS coupons (
    id          BIGINT         NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    amount      DECIMAL(12,4)  NOT NULL DEFAULT 0.0000 COMMENT '优惠金额（元）',
    description VARCHAR(512)   NOT NULL DEFAULT '' COMMENT '描述',
    user_id     BIGINT         NOT NULL COMMENT '用户ID',
    create_time DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券表';


-- ============================================================
-- 6. api_request_logs — new-api请求日志同步表
-- ============================================================
CREATE TABLE IF NOT EXISTS api_request_logs (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '本地自增主键',
    user_id             BIGINT       NOT NULL DEFAULT 0 COMMENT '用户ID',

    -- 去重键
    request_id          VARCHAR(64)  NOT NULL COMMENT 'new-api请求追踪ID',
    type                TINYINT      NOT NULL DEFAULT 2 COMMENT '日志类型：2=消费 5=错误',

    -- 业务字段
    upstream_request_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '上游请求ID',
    created_at          BIGINT       NOT NULL COMMENT '请求时间，Unix秒',
    model_name          VARCHAR(128) NOT NULL DEFAULT '' COMMENT '模型名称',
    token_name          VARCHAR(128) NOT NULL DEFAULT '' COMMENT '令牌名称',
    channel_id          INT          NOT NULL DEFAULT 0 COMMENT '渠道ID',
    prompt_tokens       INT          NOT NULL DEFAULT 0 COMMENT '提示词Token数',
    completion_tokens   INT          NOT NULL DEFAULT 0 COMMENT '补全Token数',
    quota               INT          NOT NULL DEFAULT 0 COMMENT '消耗配额',
    use_time            INT          NOT NULL DEFAULT 0 COMMENT '请求耗时（秒）',
    is_stream           TINYINT      NOT NULL DEFAULT 0 COMMENT '是否流式：0=否 1=是',
    content             TEXT         COMMENT '日志内容',
    other               TEXT         COMMENT '扩展JSON（计费详情等）',
    ip                  VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '客户端IP',
    group_col           VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '分组标识',
    new_api_log_id      INT          NOT NULL DEFAULT 0 COMMENT '原始log.id，仅参考',

    -- 同步管理
    synced_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '同步时间',
    sync_batch          VARCHAR(36)  COMMENT '批次号',

    PRIMARY KEY (id),
    UNIQUE KEY uk_request_type (request_id, type),
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at),
    INDEX idx_model_name (model_name),
    INDEX idx_synced_at (synced_at)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='new-api请求日志同步表';


-- ============================================================
-- 7. dashboard_hourly — 按模型+小时聚合表（等效 new-api quota_data）
-- ============================================================
CREATE TABLE IF NOT EXISTS dashboard_hourly (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    user_id           BIGINT       NOT NULL DEFAULT 0 COMMENT '用户ID',

    -- 维度
    model_name        VARCHAR(128) NOT NULL COMMENT '模型名称',
    hour_bucket       BIGINT       NOT NULL COMMENT '小时桶，Unix秒（created_at 整除 3600）',
    channel_id        INT          NOT NULL DEFAULT 0 COMMENT '渠道ID',

    -- 聚合值
    request_count     INT          NOT NULL DEFAULT 0 COMMENT '请求次数',
    quota             INT          NOT NULL DEFAULT 0 COMMENT '配额消耗（合计）',
    prompt_tokens     INT          NOT NULL DEFAULT 0 COMMENT '提示词Token数（合计）',
    completion_tokens INT          NOT NULL DEFAULT 0 COMMENT '补全Token数（合计）',
    token_used        INT          NOT NULL DEFAULT 0 COMMENT 'Token总用量（prompt+completion）',
    use_time          INT          NOT NULL DEFAULT 0 COMMENT '请求总耗时，秒',
    stream_count      INT          NOT NULL DEFAULT 0 COMMENT '流式请求次数',

    PRIMARY KEY (id),
    UNIQUE KEY uk_user_model_hour_channel (user_id, model_name, hour_bucket, channel_id),
    INDEX idx_hour_bucket (hour_bucket),
    INDEX idx_model_name (model_name)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='按模型+小时聚合的用量明细（等效new-api quota_data）';


-- ============================================================
-- 8. dashboard_model_summary — 按模型汇总累计表（看板卡片数据源）
-- ============================================================
CREATE TABLE IF NOT EXISTS dashboard_model_summary (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    user_id           BIGINT       NOT NULL DEFAULT 0 COMMENT '用户ID',

    model_name        VARCHAR(128) NOT NULL COMMENT '模型名称',

    -- 累计值（从开始到现在的总和）
    total_requests    INT          NOT NULL DEFAULT 0 COMMENT '总请求次数',
    total_quota       INT          NOT NULL DEFAULT 0 COMMENT '总配额消耗',
    total_prompt      INT          NOT NULL DEFAULT 0 COMMENT '总提示词Token数',
    total_completion  INT          NOT NULL DEFAULT 0 COMMENT '总补全Token数',
    total_tokens      INT          NOT NULL DEFAULT 0 COMMENT '总Token用量',
    total_use_time    INT          NOT NULL DEFAULT 0 COMMENT '总耗时，秒',
    total_stream      INT          NOT NULL DEFAULT 0 COMMENT '总流式请求次数',

    -- 最近一次
    last_request_at   BIGINT       NOT NULL DEFAULT 0 COMMENT '最近一次请求时间，Unix秒',
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_user_model (user_id, model_name)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='按模型汇总累计（看板卡片）';


-- ============================================================
-- 9. log_sync_state — 日志同步水位表
-- ============================================================
CREATE TABLE IF NOT EXISTS log_sync_state (
    id              INT          NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    user_id         BIGINT       NOT NULL DEFAULT 0 COMMENT '用户ID',
    sync_key        VARCHAR(64)  NOT NULL DEFAULT 'default' COMMENT '同步标识（预留多key场景）',
    last_synced_at  DATETIME     COMMENT '最后一次同步时间',
    last_agg_at     DATETIME     COMMENT '最后一次聚合时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_sync_key (sync_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日志同步水位记录';

-- 初始化一条水位记录
INSERT INTO log_sync_state (sync_key, last_synced_at, last_agg_at)
VALUES ('default', NULL, NULL)
ON DUPLICATE KEY UPDATE sync_key = sync_key;


-- ============================================================
-- 汇总
-- ============================================================
-- 现有表: sys_user, relay_station, relay_station_key
-- 新增表: orders, coupons, api_request_logs, dashboard_hourly,
--          dashboard_model_summary, log_sync_state
--
-- 共计: 9 张表
-- ============================================================
