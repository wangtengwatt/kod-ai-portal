-- kod 后端表结构（MySQL 8）
-- 由迁移脚本/DBA 在目标库执行；应用默认不自动执行（非嵌入式数据源）。
-- 若库不存在且账号有权限，可先执行：
--   CREATE DATABASE IF NOT EXISTS kod DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

-- 第一表：AI 中转站（url + 邀请码，邀请码唯一）
CREATE TABLE IF NOT EXISTS relay_station (
    id          BIGINT       NOT NULL COMMENT '主键（雪花）',
    url         VARCHAR(512) NOT NULL COMMENT '中转站地址',
    invite_code VARCHAR(64)  NOT NULL COMMENT '邀请码（唯一）',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_relay_station_invite_code (invite_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 中转站';

-- 第二表：中转站 API 密钥（关联第一表主键）
CREATE TABLE IF NOT EXISTS relay_station_key (
    id          BIGINT       NOT NULL COMMENT '主键（雪花）',
    station_id  BIGINT       NOT NULL COMMENT '所属中转站主键',
    api_key     VARCHAR(256) NOT NULL COMMENT 'API 密钥',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_relay_station_key_station_id (station_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '中转站 API 密钥';

-- 用户表（邮箱唯一，密码 BCrypt 加密，station_id 注册时由邀请码关联）
CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT       NOT NULL COMMENT '主键（雪花）',
    email       VARCHAR(128) NOT NULL COMMENT '登录邮箱（唯一）',
    password    VARCHAR(128) NOT NULL COMMENT 'BCrypt 密码',
    station_id  BIGINT       DEFAULT NULL COMMENT '关联中转站主键',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_email (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户';
