-- kod 后端表结构
-- dev(H2) 环境自动执行；test/prod(MySQL) 仅作参考，建表交由迁移脚本/DBA 执行。

-- 第一表：AI 中转站（url + 邀请码，邀请码唯一）
CREATE TABLE IF NOT EXISTS relay_station (
    id          BIGINT       NOT NULL PRIMARY KEY,
    url         VARCHAR(512) NOT NULL,
    invite_code VARCHAR(64)  NOT NULL,
    create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_relay_station_invite_code ON relay_station (invite_code);

-- 第二表：中转站 API 密钥（关联第一表主键）
CREATE TABLE IF NOT EXISTS relay_station_key (
    id          BIGINT       NOT NULL PRIMARY KEY,
    station_id  BIGINT       NOT NULL,
    api_key     VARCHAR(256) NOT NULL,
    create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_relay_station_key_station_id ON relay_station_key (station_id);

-- 用户表（邮箱唯一，密码 BCrypt 加密，station_id 注册时由邀请码关联）
CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT       NOT NULL PRIMARY KEY,
    email       VARCHAR(128) NOT NULL,
    password    VARCHAR(128) NOT NULL,
    station_id  BIGINT,
    create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_user_email ON sys_user (email);
