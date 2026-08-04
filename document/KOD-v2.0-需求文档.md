# KOD v2.0 接口设计文档

> **基于**: PRD 2026-07-31  
> **涉及项目**: kod-ai-portal（官网后端+前端）、kod（桌面客户端）、new-api（AI网关）  
> **设计原则**: 尽量不修改原生接口，新增接口以扩展为主

---

## 目录

1. [总体架构](#1-总体架构)
2. [数据库变更](#2-数据库变更)
3. [kod-ai-portal 后端新增接口](#3-kod-ai-portal-后端新增接口)
   - [3.1 钱包相关](#31-钱包相关)
   - [3.2 看板相关](#32-看板相关)
   - [3.3 中转站扩展](#33-中转站扩展)
   - [3.4 客户端会话管理](#34-客户端会话管理)
   - [3.5 外部支付回调](#35-外部支付回调)
   - [3.6 日志同步](#36-日志同步)
4. [kod-ai-portal 前端新增页面](#4-kod-ai-portal-前端新增页面)
5. [KOD 客户端接口对接](#5-kod-客户端接口对接)
6. [日志同步与聚合流程](#6-日志同步与聚合流程)
7. [接口清单总览](#7-接口清单总览)

---

## 1. 总体架构

```
┌──────────────────────────────────────────────────────────────────┐
│                        KOD 生态 v2.0                              │
│                                                                   │
│  ┌─────────────────┐    ┌──────────────────┐                     │
│  │  kod-ai-portal   │    │     new-api       │                    │
│  │  (Spring Boot)   │    │    (Go/Gin)       │                    │
│  │                  │    │                   │                    │
│  │ • 用户注册/登录   │    │ • AI API 代理      │                    │
│  │ • 钱包/充值      │◄──►│ • 日志 /api/log/   │                    │
│  │ • 数据看板       │    │   token            │                    │
│  │ • 中转站管理     │    │ • 外部支付(epay/   │                    │
│  │ • 客户端会话     │    │   stripe/creem…)   │                    │
│  │ • 日志同步轮询   │    │                    │                    │
│  └────────┬────────┘    └──────────────────┘                     │
│           │                                                       │
│           ▼                                                       │
│  ┌─────────────────┐                                              │
│  │   kod 桌面客户端  │                                              │
│  │   (Electron)     │                                              │
│  │                  │                                              │
│  │ • 中转站选择     │                                              │
│  │ • API Key 选择   │                                              │
│  │ • AI 对话        │                                              │
│  └─────────────────┘                                              │
└──────────────────────────────────────────────────────────────────┘
```

### 数据流

```
用户充值 → kod-ai-portal 后端 → 外部支付(epay/stripe) → 回调 → 更新 user.balance
                                                                    │
用户发消息 → kod客户端 → kod-ai-portal /api/session/send → 检查余额  │
                                                              │     │
                                                    余额>0 → 转发到中转站(new-api)
                                                              │
                                                    返回响应 + 扣除余额
                                                              │
日志同步 → kod-ai-portal 定时轮询 new-api /api/log/token
              │
              ├→ 写入 api_request_logs
              ├→ 聚合到 dashboard_hourly
              └→ 聚合到 dashboard_model_summary
```

---

## 2. 数据库变更

### 2.1 现有表字段扩展

#### sys_user 表新增字段

```sql
ALTER TABLE sys_user
    ADD COLUMN balance            DECIMAL(12,4) NOT NULL DEFAULT 0.0000 COMMENT '余额（元）',
    ADD COLUMN historical_consumption DECIMAL(12,4) NOT NULL DEFAULT 0.0000 COMMENT '历史累计消耗（元）',
    ADD COLUMN connect            BIGINT        NULL COMMENT '当前连接的apikey_id，FK → relay_station_key.id';
```

#### relay_station_key 表新增字段

```sql
ALTER TABLE relay_station_key
    ADD COLUMN status TINYINT NOT NULL DEFAULT 0 COMMENT '占用状态：0=空闲(绿点) 1=占用中(红点)';
```

### 2.2 新建表

#### 订单表 (orders)

```sql
CREATE TABLE orders (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    user_id         BIGINT        NOT NULL COMMENT '用户ID',
    product_name    VARCHAR(256)  NOT NULL DEFAULT '' COMMENT '商品名称',
    payment_method  VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '支付方式：alipay/wxpay/stripe/creem',
    payment_provider VARCHAR(32)  NOT NULL DEFAULT 'epay' COMMENT '支付提供商：epay/stripe/creem',
    amount          DECIMAL(12,4) NOT NULL DEFAULT 0.0000 COMMENT '所需金额（元）',
    actual_payment  DECIMAL(12,4) NOT NULL DEFAULT 0.0000 COMMENT '实付金额（元）',
    order_no        VARCHAR(128)  NOT NULL DEFAULT '' COMMENT '订单号（唯一）',
    status          VARCHAR(32)   NOT NULL DEFAULT 'pending' COMMENT '订单状态：pending/success/failed/expired',
    coupon_id       BIGINT        NULL COMMENT '优惠券ID',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    UNIQUE KEY uk_order_no (order_no),
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';
```

#### 优惠券表 (coupons)

```sql
CREATE TABLE coupons (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    amount      DECIMAL(12,4) NOT NULL DEFAULT 0.0000 COMMENT '优惠金额（元）',
    description VARCHAR(512)  NOT NULL DEFAULT '' COMMENT '描述',
    user_id     BIGINT        NOT NULL COMMENT '用户ID',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券表';
```

#### 日志同步表 (api_request_logs)

```sql
CREATE TABLE api_request_logs (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '本地自增主键',
    request_id          VARCHAR(64)  NOT NULL COMMENT 'new-api请求追踪ID',
    type                TINYINT      NOT NULL DEFAULT 2 COMMENT '日志类型：2=消费 5=错误',
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
    synced_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '同步时间',
    sync_batch          VARCHAR(36)  COMMENT '批次号',

    UNIQUE KEY uk_request_type (request_id, type),
    INDEX idx_created_at (created_at),
    INDEX idx_model_name (model_name),
    INDEX idx_synced_at (synced_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='new-api请求日志同步表';
```

#### 看板小时聚合表 (dashboard_hourly)

```sql
CREATE TABLE dashboard_hourly (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    model_name        VARCHAR(128) NOT NULL COMMENT '模型名称',
    hour_bucket       BIGINT       NOT NULL COMMENT '小时桶，Unix秒（created_at 整除 3600）',
    channel_id        INT          NOT NULL DEFAULT 0 COMMENT '渠道ID',
    request_count     INT          NOT NULL DEFAULT 0 COMMENT '请求次数',
    quota             INT          NOT NULL DEFAULT 0 COMMENT '配额消耗（合计）',
    prompt_tokens     INT          NOT NULL DEFAULT 0 COMMENT '提示词Token数（合计）',
    completion_tokens INT          NOT NULL DEFAULT 0 COMMENT '补全Token数（合计）',
    token_used        INT          NOT NULL DEFAULT 0 COMMENT 'Token总用量（prompt+completion）',
    use_time          INT          NOT NULL DEFAULT 0 COMMENT '请求总耗时，秒',
    stream_count      INT          NOT NULL DEFAULT 0 COMMENT '流式请求次数',

    UNIQUE KEY uk_model_hour_channel (model_name, hour_bucket, channel_id),
    INDEX idx_hour_bucket (hour_bucket),
    INDEX idx_model_name (model_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='按模型+小时聚合的用量明细';
```

#### 看板模型汇总表 (dashboard_model_summary)

```sql
CREATE TABLE dashboard_model_summary (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    model_name        VARCHAR(128) NOT NULL COMMENT '模型名称',
    total_requests    INT          NOT NULL DEFAULT 0 COMMENT '总请求次数',
    total_quota       INT          NOT NULL DEFAULT 0 COMMENT '总配额消耗',
    total_prompt      INT          NOT NULL DEFAULT 0 COMMENT '总提示词Token数',
    total_completion  INT          NOT NULL DEFAULT 0 COMMENT '总补全Token数',
    total_tokens      INT          NOT NULL DEFAULT 0 COMMENT '总Token用量',
    total_use_time    INT          NOT NULL DEFAULT 0 COMMENT '总耗时，秒',
    total_stream      INT          NOT NULL DEFAULT 0 COMMENT '总流式请求次数',
    last_request_at   BIGINT       NOT NULL DEFAULT 0 COMMENT '最近一次请求时间，Unix秒',
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_model (model_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='按模型汇总累计（看板卡片）';
```

#### 日志同步水位表

```sql
CREATE TABLE log_sync_state (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    last_synced_at  DATETIME COMMENT '最后一次同步时间',
    last_agg_at     DATETIME COMMENT '最后一次聚合时间',
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日志同步水位记录';
```

---

## 3. kod-ai-portal 后端新增接口

> **基础路径**: `/api`  
> **统一响应格式**: `Result<T>`  
> ```json
> { "code": 0, "message": "success", "data": <T> }
> ```
> **认证方式**: JWT Bearer Token（`Authorization: Bearer <token>`），沿用现有 JwtUtil

---

### 3.1 钱包相关

钱包功能对标 new-api 的钱包页面，接口字段保持一致。

#### 3.1.1 获取充值配置信息

```
GET /api/user/topup/info
Auth: JWT Bearer Token
```

**请求**: 无

**响应 `data` 字段**:

```json
{
  "enable_online_topup": true,
  "enable_stripe_topup": false,
  "enable_creem_topup": false,
  "enable_waffo_topup": false,
  "enable_waffo_pancake_topup": false,
  "payment_compliance_confirmed": true,
  "payment_compliance_terms_version": "v1",
  "min_topup": 1,
  "stripe_min_topup": 1,
  "waffo_min_topup": 1,
  "waffo_pancake_min_topup": 1,
  "amount_options": [1, 5, 10, 20, 50, 100],
  "discount": {},
  "pay_methods": [
    {
      "name": "支付宝",
      "type": "alipay",
      "color": "#1677FF",
      "min_topup": "1"
    },
    {
      "name": "微信支付",
      "type": "wxpay",
      "color": "#07C160",
      "min_topup": "1"
    }
  ],
  "topup_link": "",
  "waffo_pay_methods": null,
  "creem_products": "[]"
}
```

**字段说明**:

| 字段 | 类型 | 说明 |
|------|------|------|
| `enable_online_topup` | boolean | 是否启用在线充值（epay） |
| `enable_stripe_topup` | boolean | 是否启用 Stripe 充值 |
| `enable_creem_topup` | boolean | 是否启用 Creem 充值 |
| `enable_waffo_topup` | boolean | 是否启用 Waffo 充值 |
| `enable_waffo_pancake_topup` | boolean | 是否启用 Waffo Pancake 充值 |
| `payment_compliance_confirmed` | boolean | 支付合规是否已确认 |
| `payment_compliance_terms_version` | string | 支付合规条款版本 |
| `min_topup` | int | 最低充值金额 |
| `amount_options` | int[] | 可选充值金额列表 |
| `discount` | object | 折扣配置（key:金额, value:折扣率） |
| `pay_methods` | array | 可用支付方式列表 |
| `pay_methods[].name` | string | 支付方式名称 |
| `pay_methods[].type` | string | 支付方式类型标识 |
| `pay_methods[].color` | string | 前端展示颜色 |
| `pay_methods[].min_topup` | string | 该方式最低充值金额 |

**后端实现**: 从配置文件（`application.yml`）读取支付配置项返回。

---

#### 3.1.2 计算充值金额

```
POST /api/user/amount
Auth: JWT Bearer Token
```

**请求体**:

```json
{
  "amount": 10
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `amount` | int | 是 | 充值面额 |

**响应 `data` 字段**: `"5.00"` (字符串，实际支付金额，保留两位小数)

**后端实现**: 根据配置的折扣/汇率计算实际支付金额。若无折扣则 `payMoney = amount`。

---

#### 3.1.3 发起支付（Epay）

```
POST /api/user/pay
Auth: JWT Bearer Token
```

**请求体**:

```json
{
  "amount": 10,
  "payment_method": "alipay"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `amount` | int | 是 | 充值面额 |
| `payment_method` | string | 是 | 支付方式：`alipay` / `wxpay` |

**响应**:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "order_no": "USR123NOabc456",
    "payment_url": "https://epay.example.com/pay?id=xxx"
  },
  "url": "https://epay.example.com/pay?id=xxx"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `data.order_no` | string | 订单号 |
| `data.payment_url` | string | 支付跳转URL |
| `url` | string | 同 payment_url（兼容新API格式） |

**后端实现流程**:
1. 校验 `payment_method` 在配置的可用支付方式中
2. 创建 `orders` 记录（status=pending）
3. 调用 Epay 接口获取支付链接
4. 返回支付 URL 和订单号

---

#### 3.1.4 获取充值记录

```
GET /api/user/topup/self?p=1&page_size=10
Auth: JWT Bearer Token
```

**查询参数**:

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `p` | int | 否 | 1 | 页码 |
| `page_size` | int | 否 | 10 | 每页条数 |

**响应 `data` 字段**:

```json
{
  "items": [
    {
      "id": 1,
      "user_id": 1900000000000000001,
      "amount": 10,
      "money": 10.00,
      "trade_no": "USR123NOabc456",
      "payment_method": "alipay",
      "payment_provider": "epay",
      "create_time": 1754000000,
      "complete_time": 0,
      "status": "success"
    }
  ],
  "total": 1,
  "page": 1,
  "page_size": 10
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | int | 记录ID |
| `user_id` | int | 用户ID |
| `amount` | int | 充值面额 |
| `money` | float64 | 实际支付金额 |
| `trade_no` | string | 订单号 |
| `payment_method` | string | 支付方式 |
| `payment_provider` | string | 支付提供商：`epay`/`stripe`/`creem` |
| `create_time` | int64 | 创建时间（Unix秒） |
| `complete_time` | int64 | 完成时间（Unix秒，0=未完成） |
| `status` | string | 状态：`pending`/`success`/`failed`/`expired` |

---

#### 3.1.5 获取钱包余额

```
GET /api/user/wallet
Auth: JWT Bearer Token
```

**请求**: 无

**响应 `data` 字段**:

```json
{
  "balance": 100.50,
  "historical_consumption": 25.30
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `balance` | decimal | 当前余额（元） |
| `historical_consumption` | decimal | 历史累计消耗（元） |

**后端实现**: 从 JWT 解析 userId，查询 `sys_user` 表返回 `balance` 和 `historical_consumption`。

---

### 3.2 看板相关

看板数据来源于 `dashboard_model_summary` 和 `dashboard_hourly` 两张聚合表，对标 new-api Dashboard 页面。

#### 3.2.1 看板总览

```
GET /api/dashboard/overview
Auth: JWT Bearer Token
```

**请求**: 无

**响应 `data` 字段**:

```json
{
  "quota_used_all": 1560000,
  "tokens_all": 5200000,
  "requests_all": 1234,
  "use_time_all_sec": 36000
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `quota_used_all` | int | 总配额消耗 |
| `tokens_all` | int | 总Token用量 |
| `requests_all` | int | 总请求次数 |
| `use_time_all_sec` | int | 总耗时（秒） |

**后端SQL**:

```sql
SELECT
    COALESCE(SUM(total_quota), 0)    AS quota_used_all,
    COALESCE(SUM(total_tokens), 0)   AS tokens_all,
    COALESCE(SUM(total_requests), 0) AS requests_all,
    COALESCE(SUM(total_use_time), 0) AS use_time_all_sec
FROM dashboard_model_summary;
```

---

#### 3.2.2 模型用量排行榜

```
GET /api/dashboard/models?limit=20
Auth: JWT Bearer Token
```

**查询参数**:

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `limit` | int | 否 | 20 | 返回条数 |

**响应 `data` 字段**:

```json
[
  {
    "model_name": "gpt-4o",
    "total_quota": 500000,
    "total_tokens": 1600000,
    "total_requests": 400,
    "last_request_at": 1754006400
  },
  {
    "model_name": "claude-sonnet-5",
    "total_quota": 350000,
    "total_tokens": 1200000,
    "total_requests": 300,
    "last_request_at": 1754006200
  }
]
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `model_name` | string | 模型名称 |
| `total_quota` | int | 总配额消耗 |
| `total_tokens` | int | 总Token用量 |
| `total_requests` | int | 总请求次数 |
| `last_request_at` | int64 | 最近一次请求时间（Unix秒） |

**后端SQL**:

```sql
SELECT model_name, total_quota, total_tokens, total_requests, last_request_at
FROM dashboard_model_summary
WHERE total_quota > 0
ORDER BY total_quota DESC
LIMIT ?;
```

---

#### 3.2.3 时间趋势图

```
GET /api/dashboard/hourly?models=gpt-4o,claude-sonnet-5&hours=24
Auth: JWT Bearer Token
```

**查询参数**:

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `models` | string | 是 | - | 模型名称，逗号分隔 |
| `hours` | int | 否 | 24 | 最近多少小时 |

**响应 `data` 字段**:

```json
[
  {
    "model_name": "gpt-4o",
    "points": [
      {
        "hour_bucket": 1754002800,
        "label": "07-31 15:00",
        "quota": 25000,
        "request_count": 20,
        "token_used": 80000
      },
      {
        "hour_bucket": 1754006400,
        "label": "07-31 16:00",
        "quota": 30000,
        "request_count": 25,
        "token_used": 95000
      }
    ]
  }
]
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `model_name` | string | 模型名称 |
| `points[].hour_bucket` | int64 | 小时桶（Unix秒） |
| `points[].label` | string | 格式化时间标签 |
| `points[].quota` | int | 配额消耗 |
| `points[].request_count` | int | 请求次数 |
| `points[].token_used` | int | Token用量 |

**后端SQL**:

```sql
SELECT model_name, hour_bucket,
       FROM_UNIXTIME(hour_bucket, '%m-%d %H:00') AS label,
       quota, request_count, token_used
FROM dashboard_hourly
WHERE model_name IN (?, ?, ...)
  AND hour_bucket >= UNIX_TIMESTAMP() - ? * 3600
ORDER BY hour_bucket ASC;
```

---

#### 3.2.4 渠道占比（饼图）

```
GET /api/dashboard/channel-distribution?start_ts=1754000000&end_ts=1754086400
Auth: JWT Bearer Token
```

**查询参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `start_ts` | int64 | 是 | 开始时间（Unix秒） |
| `end_ts` | int64 | 是 | 结束时间（Unix秒） |

**响应 `data` 字段**:

```json
[
  {
    "channel_id": 3,
    "quota": 500000
  },
  {
    "channel_id": 5,
    "quota": 300000
  }
]
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `channel_id` | int | 渠道ID |
| `quota` | int | 配额消耗 |

---

#### 3.2.5 最近请求记录

```
GET /api/dashboard/recent-logs?limit=50
Auth: JWT Bearer Token
```

**查询参数**:

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `limit` | int | 否 | 50 | 返回条数 |

**响应 `data` 字段**:

```json
[
  {
    "id": 1,
    "request_id": "req-abc-001",
    "created_at": 1754006400,
    "model_name": "gpt-4o",
    "quota": 1500,
    "prompt_tokens": 150,
    "completion_tokens": 300,
    "use_time": 2,
    "is_stream": true
  }
]
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | int64 | 本地主键 |
| `request_id` | string | 请求追踪ID |
| `created_at` | int64 | 请求时间（Unix秒） |
| `model_name` | string | 模型名称 |
| `quota` | int | 消耗配额 |
| `prompt_tokens` | int | 提示词Token数 |
| `completion_tokens` | int | 补全Token数 |
| `use_time` | int | 请求耗时（秒） |
| `is_stream` | boolean | 是否流式 |

---

#### 3.2.6 日志用量统计

```
GET /api/dashboard/log-stat?start_timestamp=1754000000&end_timestamp=1754086400
Auth: JWT Bearer Token
```

**查询参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `start_timestamp` | int64 | 否 | 开始时间（Unix秒） |
| `end_timestamp` | int64 | 否 | 结束时间（Unix秒） |

**响应 `data` 字段**:

```json
{
  "quota": 1234567,
  "rpm": 42,
  "tpm": 150000
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `quota` | int | 时间段内总配额消耗 |
| `rpm` | int | 最近60秒请求数（Requests Per Minute） |
| `tpm` | int | 最近60秒Token数（Tokens Per Minute） |

---

### 3.3 中转站扩展

为 KOD 客户端提供中转站列表和密钥管理接口。

#### 3.3.1 获取所有中转站列表

```
GET /api/relay-station/list
Auth: None
```

**请求**: 无

**响应 `data` 字段**:

```json
[
  {
    "id": 1900000000000000001,
    "url": "https://fane.kai.com/v1",
    "create_time": "2026-07-16T10:00:00"
  },
  {
    "id": 1900000000000000002,
    "url": "https://api.kai.com/v1",
    "create_time": "2026-07-20T14:00:00"
  }
]
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | int64 | 中转站ID |
| `url` | string | 中转站URL |
| `create_time` | string | 创建时间（ISO格式） |

---

#### 3.3.2 获取中转站下的 API Key 列表

```
GET /api/relay-station/{stationId}/keys
Auth: None (客户端场景) / JWT Bearer Token (管理场景)
```

**路径参数**:

| 参数 | 类型 | 说明 |
|------|------|------|
| `stationId` | int64 | 中转站ID |

**响应 `data` 字段**:

```json
[
  {
    "id": 1900000000000000003,
    "station_id": 1900000000000000001,
    "api_key": "sk-xxxxxx",
    "status": 0,
    "create_time": "2026-07-16T10:30:00"
  },
  {
    "id": 1900000000000000004,
    "station_id": 1900000000000000001,
    "api_key": "sk-yyyyyy",
    "status": 1,
    "create_time": "2026-07-16T10:31:00"
  }
]
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | int64 | API Key ID |
| `station_id` | int64 | 所属中转站ID |
| `api_key` | string | API Key 值 |
| `status` | int | 状态：`0`=空闲（绿点），`1`=占用中（红点） |
| `create_time` | string | 创建时间（ISO格式） |

**排序规则**: `status=0`（空闲）排前面，`status=1`（占用中）排后面

**后端SQL**:

```sql
SELECT id, station_id, api_key, status, create_time
FROM relay_station_key
WHERE station_id = ?
ORDER BY status ASC, id ASC;
```

---

### 3.4 客户端会话管理

KOD 客户端发送消息前需要的接口。

#### 3.4.1 选择/切换 API Key

> 客户端用户选择某个中转站下的某个绿点 API Key 时调用。

```
POST /api/session/select-key
Auth: JWT Bearer Token
```

**请求体**:

```json
{
  "station_id": 1900000000000000001,
  "api_key_id": 1900000000000000003
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `station_id` | int64 | 是 | 选择的中转站ID |
| `api_key_id` | int64 | 是 | 选择的API Key ID |

**响应**:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "api_key_id": 1900000000000000003,
    "previous_api_key_id": null,
    "url": "https://fane.kai.com/v1",
    "api_key": "sk-xxxxxx"
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `api_key_id` | int64 | 当前选中的 API Key ID |
| `previous_api_key_id` | int64/null | 之前连接的 API Key ID（null=之前无连接） |
| `url` | string | 中转站URL（供客户端直接使用） |
| `api_key` | string | API Key 值（供客户端直接使用） |

**后端实现流程**:

```
1. 从JWT解析userId
2. 查询 user.connect 字段
3. 如果 user.connect 有值（之前连过其他key）:
   a. 拿到 oldApiKeyId
   b. UPDATE relay_station_key SET status=0 WHERE id=oldApiKeyId
4. 如果 user.connect 为null（首次连接）:
   a. 跳过释放步骤
5. UPDATE relay_station_key SET status=1 WHERE id=api_key_id
6. UPDATE sys_user SET connect=api_key_id WHERE id=userId
7. 查询 relay_station 获取 url
8. 返回结果
```

**事务要求**: 整个流程必须在数据库事务中执行，防止并发问题。

---

#### 3.4.2 释放 API Key

> 客户端断开连接或用户主动释放时调用。

```
POST /api/session/release-key
Auth: JWT Bearer Token
```

**请求体**: 无

**响应**:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "released_api_key_id": 1900000000000000003
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `released_api_key_id` | int64/null | 被释放的 API Key ID（null=没有可释放的） |

**后端实现流程**:

```
1. 从JWT解析userId
2. 查询用户: SELECT connect FROM sys_user WHERE id=userId
3. 如果 connect 有值:
   a. UPDATE relay_station_key SET status=0 WHERE id=connect
   b. UPDATE sys_user SET connect=NULL WHERE id=userId
4. 返回 released_api_key_id
```

---

#### 3.4.3 查询用户余额

> KOD 客户端发送消息前检查余额。

```
GET /api/session/balance
Auth: JWT Bearer Token
```

**请求**: 无

**响应 `data` 字段**:

```json
{
  "balance": 100.50,
  "can_chat": true
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `balance` | decimal | 当前余额（元） |
| `can_chat` | boolean | 是否可以发起对话（balance > 0） |

---

#### 3.4.4 发送消息（代理请求）

> **说明**: 此接口为 KOD 客户端直接调用中转站的代理。实际上客户端拿到 url 和 api_key 后直接调用中转站 /v1/chat/completions，此接口保留用于需要服务端代理的场景。
>
> **注意**: PRD 描述客户端拿到 url + api_key 后直接发送请求到中转站。但**日志同步**和**余额扣减**仍需要 kod-ai-portal 后端处理，此为服务端代理接口。

```
POST /api/session/chat
Auth: JWT Bearer Token
```

**请求体** (透传 OpenAI Chat Completions 格式):

```json
{
  "model": "gpt-4o",
  "messages": [
    { "role": "user", "content": "Hello" }
  ],
  "stream": true
}
```

**后端实现流程**:

```
1. 从JWT解析userId
2. 查询用户信息: SELECT id, balance, connect FROM sys_user WHERE id=userId
3. 校验:
   a. balance <= 0 → 返回 {"code": 402, "message": "余额不足，请充值"}
   b. connect 为 null → 返回 {"code": 400, "message": "请先选择中转站和API Key"}
4. 查询中转站信息:
   SELECT rs.url, rsk.api_key
   FROM relay_station_key rsk
   JOIN relay_station rs ON rsk.station_id = rs.id
   WHERE rsk.id = user.connect
5. 转发请求到 {url}/v1/chat/completions，携带 Authorization: Bearer {api_key}
6. 启动日志轮询（LogSyncService.onUserClickSend）
7. 流式返回响应给客户端
8. （日志轮询后台线程持续运行，直到客户端断开）
```

**错误码**:

| code | HTTP Status | message | 说明 |
|------|-------------|---------|------|
| 402 | 200 | 余额不足，请充值 | balance ≤ 0 |
| 400 | 200 | 请先选择中转站和API Key | connect 为 null |
| 500 | 200 | 中转站请求失败：{详情} | 上游请求异常 |

---

### 3.5 外部支付回调

#### 3.5.1 Epay 支付回调

```
POST /api/payment/epay/notify
Auth: None（Epay 签名验证）
```

**请求**: Epay 标准回调参数（form-urlencoded / query params）

**后端实现流程**:

```
1. 验证 Epay 签名
2. 如果 TRADE_SUCCESS:
   a. 根据 trade_no 查询 orders 表
   b. 如果订单状态 != pending → 返回 "success"（幂等）
   c. UPDATE orders SET status='success', actual_payment=amount, update_time=NOW()
   d. UPDATE sys_user SET balance = balance + order.amount WHERE id=order.user_id
   e. 写入 api_request_logs 记账 (type=1 topup)
3. 返回 "success"
```

**响应**: 纯文本 `"success"` 或 `"fail"`

---

#### 3.5.2 Stripe Webhook

```
POST /api/payment/stripe/webhook
Auth: None（Stripe 签名验证）
```

**后端实现流程**:

```
1. 验证 Stripe webhook 签名
2. 处理 checkout.session.completed:
   a. payment_status=paid → 完成订单、增加余额
   b. payment_status≠paid → 标记订单失败
3. 处理 checkout.session.expired:
   a. 标记订单状态为 expired
```

**响应**: HTTP 200 `{"received": true}` 或 400

---

### 3.6 日志同步

#### 3.6.1 手动触发日志同步

```
POST /api/log/sync
Auth: JWT Bearer Token
```

**请求体**: 无

**响应**:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "synced_count": 5,
    "batch_id": "uuid-xxxx"
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `synced_count` | int | 本次同步写入的日志条数 |
| `batch_id` | string | 批次ID |

**后端实现**: 手动执行一次 syncOnce 逻辑（详见 [6. 日志同步与聚合流程](#6-日志同步与聚合流程)）

---

#### 3.6.2 获取同步状态

```
GET /api/log/sync-status
Auth: JWT Bearer Token
```

**响应 `data` 字段**:

```json
{
  "syncing": true,
  "last_synced_at": "2026-07-31T12:00:00",
  "last_agg_at": "2026-07-31T11:55:00",
  "total_logs_synced": 15230
}
```

---

## 4. kod-ai-portal 前端新增页面

### 4.1 页面路由

| 路径 | 页面 | 说明 |
|------|------|------|
| `/wallet` | 钱包页 | 对标 new-api 钱包 UI |
| `/dashboard` | 数据看板 | 对标 new-api Dashboard |

### 4.2 钱包页 (`/wallet`)

**UI 对标**: new-api `/console/topup` 页面

**页面组成**:

| 区域 | 组件 | 调用接口 |
|------|------|----------|
| 余额展示 | 卡片，显示 `balance` 和历史消耗 | `GET /api/user/wallet` |
| 充值金额选择 | 按钮组，从 `amount_options` 渲染 | `GET /api/user/topup/info` |
| 支付方式选择 | 按钮组，从 `pay_methods` 渲染 | `GET /api/user/topup/info` |
| 充值按钮 | 点击后调起支付 | `POST /api/user/pay` |
| 充值记录 | 表格，分页 | `GET /api/user/topup/self` |

**充值流程**:

```
1. 用户选择金额 + 支付方式
2. 前端调用 POST /api/user/pay → 获取 payment_url
3. 新窗口打开 payment_url 或当前页面跳转
4. 用户完成支付 → Epay 回调 → 后端更新余额
5. 用户返回钱包页，刷新余额
```

### 4.3 数据看板 (`/dashboard`)

**UI 对标**: new-api `/dashboard/overview` 和 `/dashboard/models` 页面

**页面组成**:

| 区域 | 组件 | 调用接口 |
|------|------|----------|
| 总览卡片 | Stat cards (总配额/总Token/总请求/总耗时) | `GET /api/dashboard/overview` |
| 模型排行榜 | 柱状图/表格 | `GET /api/dashboard/models` |
| 时间趋势图 | 折线图（按模型） | `GET /api/dashboard/hourly` |
| 渠道占比 | 饼图 | `GET /api/dashboard/channel-distribution` |
| 最近请求 | 表格 | `GET /api/dashboard/recent-logs` |

**数据刷新**: 看板页面加载时全量获取，提供手动刷新按钮，不自动轮询。

---

## 5. KOD 客户端接口对接

### 5.1 客户端调用时序

```
┌─────────────┐       ┌──────────────────┐       ┌──────────┐
│  KOD 客户端   │       │  kod-ai-portal    │       │  new-api  │
│  (Electron)  │       │  (Spring Boot)    │       │  (中转站)  │
└──────┬───────┘       └────────┬─────────┘       └────┬─────┘
       │                        │                      │
       │  1.GET /api/relay-station/list                │
       │───────────────────────►│                      │
       │  [中转站URL列表]        │                      │
       │◄───────────────────────│                      │
       │                        │                      │
       │  2.GET /api/relay-station/{id}/keys           │
       │───────────────────────►│                      │
       │  [API Key列表+status]  │                      │
       │◄───────────────────────│                      │
       │                        │                      │
       │  用户选择中转站 + 绿点API Key                   │
       │                        │                      │
       │  3.POST /api/session/select-key               │
       │───────────────────────►│                      │
       │                        │──► UPDATE key status  │
       │                        │──► UPDATE user.connect│
       │  {url, api_key}        │                      │
       │◄───────────────────────│                      │
       │                        │                      │
       │  4.GET /api/session/balance                   │
       │───────────────────────►│                      │
       │  {balance, can_chat}   │                      │
       │◄───────────────────────│                      │
       │                        │                      │
       │  用户输入消息，点击发送   │                      │
       │                        │                      │
       │  5.POST {中转站url}/v1/chat/completions       │
       │──────────────────────────────────────────────►│
       │                        │                      │
       │  6.POST /api/log/sync  (并行，后台)            │
       │───────────────────────►│                      │
       │                        │──► GET /api/log/token│
       │                        │◄── [日志数据]        │
       │                        │──► 写入 api_request  │
       │                        │    _logs             │
       │                        │──► 聚合到 dashboard  │
       │                        │──► 扣除 user.balance │
       │                        │                      │
       │  流式响应                │                      │
       │◄──────────────────────────────────────────────│
       │                        │                      │
       │  用户关闭对话            │                      │
       │                        │                      │
       │  7.POST /api/session/release-key              │
       │───────────────────────►│                      │
       │                        │──► UPDATE key status=0│
       │                        │──► UPDATE user.connect│
       │  {released_api_key_id} │      =NULL           │
       │◄───────────────────────│                      │
```

### 5.2 客户端 UI 改动

**位置**: 新对话页面 → 模型选择器左侧

**组件层级**:

```
┌──────────────────────────────────────────┐
│  中转站: [下拉选择 ▼]                      │
│  ┌──────────────────────┐                │
│  │ ● https://fane.kai... │ ← 选中后展开   │
│  │   https://api.kai...  │                │
│  └──────────────────────┘                │
│                                            │
│  API密钥: [下拉选择 ▼]                     │
│  ┌──────────────────────┐                │
│  │ 🟢 sk-xxxx (空闲)     │ ← 绿点=可用     │
│  │ 🟢 sk-yyyy (空闲)     │                │
│  │ 🔴 sk-zzzz (占用中)   │ ← 红点=占用     │
│  └──────────────────────┘                │
│                                            │
│  模型: [下拉选择 ▼]  ← 原有组件保持不变       │
│                                            │
│  ┌──────────────────────────────────────┐  │
│  │  输入消息...                          │  │
│  └──────────────────────────────────────┘  │
└──────────────────────────────────────────┘
```

**排序规则**:
- API Key 列表按 status 排序：`status=0`（空闲/绿点）在上，`status=1`（占用中/红点）在下
- 同一 status 内按 `id` 升序

### 5.3 客户端状态管理

| 状态 | 存储位置 | 说明 |
|------|----------|------|
| 选中的中转站 URL | 客户端本地存储 | 持久化，下次打开恢复 |
| 选中的 API Key | 客户端本地存储 | 持久化，下次打开恢复 |
| 服务端 connect 状态 | kod-ai-portal DB | 通过 select-key/release-key 管理 |

---

## 6. 日志同步与聚合流程

### 6.1 轮询机制

```
用户点击"发送"
    │
    ├─ startTs = now()  ← 分界线
    │
    └─ 开始循环 ─────────────────────────────┐
          │                                   │
          GET {new-api}/api/log/token         │
          │  返回最多 1000 条（DESC）          │
          │  只取前 10 条                     │
          │                                   │
          对每条:                              │
          │  created_at < startTs → 跳过      │
          │  request_id+type 已存在 → 跳过     │
          │  其余 → INSERT IGNORE             │
          │                                   │
          执行增量聚合（dashboard_hourly +     │
          dashboard_model_summary）           │
          │                                   │
          扣除余额（根据 quota 换算为金额）     │
          │                                   │
          sleep(10s) ─────────────────────────┘
```

### 6.2 日志聚合SQL

#### 聚合到 dashboard_hourly

```sql
INSERT INTO dashboard_hourly
    (model_name, hour_bucket, channel_id,
     request_count, quota, prompt_tokens, completion_tokens, token_used, use_time, stream_count)
SELECT
    model_name,
    created_at - (created_at % 3600) AS hour_bucket,
    channel_id,
    COUNT(*)             AS request_count,
    SUM(quota)           AS quota,
    SUM(prompt_tokens)   AS prompt_tokens,
    SUM(completion_tokens) AS completion_tokens,
    SUM(prompt_tokens + completion_tokens) AS token_used,
    SUM(use_time)        AS use_time,
    SUM(CASE WHEN is_stream = 1 THEN 1 ELSE 0 END) AS stream_count
FROM api_request_logs
WHERE type = 2
  AND model_name != ''
  AND synced_at > ?
GROUP BY model_name, hour_bucket, channel_id
ON DUPLICATE KEY UPDATE
    request_count    = request_count    + VALUES(request_count),
    quota            = quota            + VALUES(quota),
    prompt_tokens    = prompt_tokens    + VALUES(prompt_tokens),
    completion_tokens = completion_tokens + VALUES(completion_tokens),
    token_used       = token_used       + VALUES(token_used),
    use_time         = use_time         + VALUES(use_time),
    stream_count     = stream_count     + VALUES(stream_count);
```

#### 聚合到 dashboard_model_summary

```sql
INSERT INTO dashboard_model_summary
    (model_name, total_requests, total_quota, total_prompt, total_completion,
     total_tokens, total_use_time, total_stream, last_request_at)
SELECT
    model_name,
    COUNT(*)             AS total_requests,
    SUM(quota)           AS total_quota,
    SUM(prompt_tokens)   AS total_prompt,
    SUM(completion_tokens) AS total_completion,
    SUM(prompt_tokens + completion_tokens) AS total_tokens,
    SUM(use_time)        AS total_use_time,
    SUM(CASE WHEN is_stream = 1 THEN 1 ELSE 0 END) AS total_stream,
    MAX(created_at)      AS last_request_at
FROM api_request_logs
WHERE type = 2
  AND model_name != ''
  AND synced_at > ?
GROUP BY model_name
ON DUPLICATE KEY UPDATE
    total_requests    = total_requests    + VALUES(total_requests),
    total_quota       = total_quota       + VALUES(total_quota),
    total_prompt      = total_prompt      + VALUES(total_prompt),
    total_completion  = total_completion  + VALUES(total_completion),
    total_tokens      = total_tokens      + VALUES(total_tokens),
    total_use_time    = total_use_time    + VALUES(total_use_time),
    total_stream      = total_stream      + VALUES(total_stream),
    last_request_at   = GREATEST(last_request_at, VALUES(last_request_at));
```

### 6.3 余额扣减

每次同步到新的消费日志后，根据 `quota` 字段换算为金额并从 `user.balance` 扣除。

```
消耗金额 = quota / QuotaPerUnit
```

> 其中 `QuotaPerUnit` 是配额单价（默认 `500000`，即 1元 = 500000 quota），该值需与 new-api 保持一致。

```sql
UPDATE sys_user
SET balance = balance - ?,
    historical_consumption = historical_consumption + ?
WHERE id = ? AND balance >= ?;
```

如果余额不足（`balance < 扣减金额`），记录告警日志，余额扣至0，不阻止请求（因为请求已经发出）。

### 6.4 Spring Boot 集成

```java
@Service
public class LogSyncService {

    @Value("${newapi.url}")
    private String newApiBaseUrl;

    @Value("${newapi.quota-per-unit:500000}")
    private int quotaPerUnit;

    private volatile boolean running = false;
    private long startTs;
    private Long userId;

    /**
     * 用户点击发送时调用
     */
    public void onUserClickSend(Long userId, String apiKey) {
        this.userId = userId;
        this.startTs = System.currentTimeMillis() / 1000;
        running = true;

        new Thread(() -> {
            while (running) {
                try {
                    int n = syncOnce(userId, apiKey, startTs);
                    if (n > 0) {
                        // 执行聚合
                        aggregateDashboard();
                        // 扣减余额
                        deductBalance(userId);
                    }
                    sleep(10_000);
                } catch (Exception e) {
                    log.error("Log sync error: {}", e.getMessage());
                    sleep(30_000);
                }
            }
        }).start();
    }

    public void onUserClose() {
        running = false;
    }
}
```

---

## 7. 接口清单总览

### 7.1 kod-ai-portal 后端新增接口（共 18 个）

| # | Method | Path | Auth | 功能 | 对标 |
|---|--------|------|------|------|------|
| **钱包** |
| 1 | `GET` | `/api/user/topup/info` | JWT | 获取充值配置 | new-api `GET /api/user/topup/info` |
| 2 | `POST` | `/api/user/amount` | JWT | 计算充值金额 | new-api `POST /api/user/amount` |
| 3 | `POST` | `/api/user/pay` | JWT | 发起支付 | new-api `POST /api/user/pay` |
| 4 | `GET` | `/api/user/topup/self` | JWT | 充值记录 | new-api `GET /api/user/topup/self` |
| 5 | `GET` | `/api/user/wallet` | JWT | 钱包余额 | 新增（对标new-api余额展示） |
| **看板** |
| 6 | `GET` | `/api/dashboard/overview` | JWT | 看板总览卡片 | new-api `GET /api/data` 聚合 |
| 7 | `GET` | `/api/dashboard/models` | JWT | 模型用量排行 | new-api RankingQuotaTotal |
| 8 | `GET` | `/api/dashboard/hourly` | JWT | 时间趋势图 | new-api quota_data 查询 |
| 9 | `GET` | `/api/dashboard/channel-distribution` | JWT | 渠道占比 | new-api flow data |
| 10 | `GET` | `/api/dashboard/recent-logs` | JWT | 最近请求记录 | new-api `GET /api/log` |
| 11 | `GET` | `/api/dashboard/log-stat` | JWT | 日志用量统计 | new-api `GET /api/log/stat` |
| **中转站扩展** |
| 12 | `GET` | `/api/relay-station/list` | None | 中转站列表 | 新增 |
| 13 | `GET` | `/api/relay-station/{stationId}/keys` | None/JWT | API Key列表 | 新增 |
| **客户端会话** |
| 14 | `POST` | `/api/session/select-key` | JWT | 选择API Key | 新增 |
| 15 | `POST` | `/api/session/release-key` | JWT | 释放API Key | 新增 |
| 16 | `GET` | `/api/session/balance` | JWT | 查询余额 | 新增 |
| 17 | `POST` | `/api/session/chat` | JWT | 代理发送消息 | 新增 |
| **日志同步** |
| 18 | `POST` | `/api/log/sync` | JWT | 手动触发同步 | 新增 |
| 19 | `GET` | `/api/log/sync-status` | JWT | 同步状态查询 | 新增 |
| **支付回调** |
| 20 | `POST/GET` | `/api/payment/epay/notify` | None | Epay回调 | new-api `/api/user/epay/notify` |
| 21 | `POST` | `/api/payment/stripe/webhook` | None | Stripe回调 | new-api `/api/stripe/webhook` |

### 7.2 现有接口保留不变

| # | Method | Path | Auth | 说明 |
|---|--------|------|------|------|
| 1 | `GET` | `/api/health` | None | 健康检查 |
| 2 | `POST` | `/api/auth/send-code` | None | 发送验证码 |
| 3 | `POST` | `/api/auth/login` | None | 登录/注册 |
| 4 | `POST` | `/api/relay-station` | None | 创建中转站 |
| 5 | `GET` | `/api/relay-station/config` | JWT | 获取中转站配置 |
| 6 | `POST` | `/api/relay-station/api-key` | JWT | 保存API Key |

### 7.3 KOD 客户端调用的接口

| # | Method | Path | 说明 | 调用时机 |
|---|--------|------|------|----------|
| 1 | `GET` | `/api/relay-station/list` | 获取中转站列表 | 打开新对话页 |
| 2 | `GET` | `/api/relay-station/{id}/keys` | 获取某站API Key | 选择了中转站后 |
| 3 | `POST` | `/api/session/select-key` | 选择API Key | 用户点击绿点Key |
| 4 | `GET` | `/api/session/balance` | 查询余额 | 发送消息前 |
| 5 | `POST` | `{url}/v1/chat/completions` | 发送消息 | 用户点击发送 → 直接调中转站(new-api) |
| 6 | `POST` | `/api/session/release-key` | 释放Key | 关闭对话/退出 |
| 7 | `POST` | `/api/log/sync` | 触发日志同步 | 发送消息后并行调用 |

### 7.4 与 new-api 原生接口的关系

| new-api 原生接口 | 是否修改 | kod-ai-portal 如何使用 |
|------------------|----------|------------------------|
| `GET /api/log/token` | **不修改** | LogSyncService 轮询调用 |
| `POST /api/user/epay/notify` | **不修改** | kod-ai-portal 自建相同接口 |
| `POST /api/stripe/webhook` | **不修改** | kod-ai-portal 自建相同接口 |
| new-api wallet 前端 | **不修改** | kod-ai-portal 前端复刻相同UI |

---

## 附录 A: 字段名对照表（交叉验证）

### A.1 kod-ai-portal ↔ new-api 字段对照

| kod-ai-portal (Java) | new-api (Go) | 说明 |
|----------------------|--------------|------|
| `User.balance` | `user.quota` / `QuotaPerUnit` | new-api用quota整数，kod-ai-portal用元(小数) |
| `User.historicalConsumption` | `user.used_quota` / `QuotaPerUnit` | 历史消耗 |
| `User.connect` | - | new-api无此概念，kod-ai-portal独有 |
| `RelayStation.url` | - | 对应new-api的base_url |
| `RelayStationKey.apiKey` | token.key | API密钥 |
| `RelayStationKey.status` | - | 0=空闲 1=占用，kod-ai-portal独有 |
| `Order.orderNo` | `TopUp.trade_no` | 订单号 |
| `Order.status` | `TopUp.status` | pending/success/failed/expired |
| `Order.amount` | `TopUp.amount` | 金额 |

### A.2 日志表字段对照

| new-api `/api/log/token` 返回 | kod-ai-portal `api_request_logs` 列 | 类型 |
|-------------------------------|-------------------------------------|------|
| `id` | `new_api_log_id` | INT |
| `request_id` | `request_id` | VARCHAR(64) |
| `type` | `type` | TINYINT |
| `upstream_request_id` | `upstream_request_id` | VARCHAR(128) |
| `created_at` | `created_at` | BIGINT |
| `model_name` | `model_name` | VARCHAR(128) |
| `token_name` | `token_name` | VARCHAR(128) |
| `channel` | `channel_id` | INT |
| `prompt_tokens` | `prompt_tokens` | INT |
| `completion_tokens` | `completion_tokens` | INT |
| `quota` | `quota` | INT |
| `use_time` | `use_time` | INT |
| `is_stream` | `is_stream` | TINYINT |
| `content` | `content` | TEXT |
| `other` | `other` | TEXT |
| `ip` | `ip` | VARCHAR(64) |

---

## 附录 B: 新增 Entity / DTO / Mapper 清单

### Entity（8个新增 + 3个修改）

| 类名 | 对应表 | 操作 |
|------|--------|------|
| `User` | `sys_user` | **修改**: 添加 balance, historicalConsumption, connect |
| `RelayStation` | `relay_station` | 不改 |
| `RelayStationKey` | `relay_station_key` | **修改**: 添加 status |
| `Order` | `orders` | **新增** |
| `Coupon` | `coupons` | **新增** |
| `ApiRequestLog` | `api_request_logs` | **新增** |
| `DashboardHourly` | `dashboard_hourly` | **新增** |
| `DashboardModelSummary` | `dashboard_model_summary` | **新增** |

### Mapper（5个新增）

| 接口 | 实体 | 操作 |
|------|------|------|
| `UserMapper` | `User` | 现有 |
| `RelayStationMapper` | `RelayStation` | 现有 |
| `RelayStationKeyMapper` | `RelayStationKey` | 现有 |
| `OrderMapper` | `Order` | **新增** |
| `CouponMapper` | `Coupon` | **新增** |
| `ApiRequestLogMapper` | `ApiRequestLog` | **新增** |
| `DashboardHourlyMapper` | `DashboardHourly` | **新增** |
| `DashboardModelSummaryMapper` | `DashboardModelSummary` | **新增** |

### DTO（8个新增）

| 类名 | 用途 |
|------|------|
| `TopUpInfoResponse` | `GET /api/user/topup/info` 响应 |
| `PayRequest` | `POST /api/user/pay` 请求 |
| `AmountRequest` | `POST /api/user/amount` 请求 |
| `TopUpItemResponse` | 充值记录单条 |
| `TopUpPageResponse` | 充值记录分页 |
| `WalletResponse` | `GET /api/user/wallet` 响应 |
| `DashboardOverviewResponse` | `GET /api/dashboard/overview` 响应 |
| `DashboardModelItem` | `GET /api/dashboard/models` 单条 |
| `DashboardHourlyPoint` | `GET /api/dashboard/hourly` 数据点 |
| `SelectKeyRequest` | `POST /api/session/select-key` 请求 |
| `SelectKeyResponse` | `POST /api/session/select-key` 响应 |
| `ReleaseKeyResponse` | `POST /api/session/release-key` 响应 |
| `BalanceResponse` | `GET /api/session/balance` 响应 |
| `SyncStatusResponse` | `GET /api/log/sync-status` 响应 |

### Service（3个新增 + 1个修改）

| 类名 | 操作 |
|------|------|
| `AuthService` | **修改**: loginOrRegister 初始化 balance=0 |
| `RelayStationService` | **修改**: saveStation 初始化 key status=0 |
| `PaymentService` | **新增**: 支付发起、回调处理 |
| `DashboardService` | **新增**: 看板数据查询 |
| `LogSyncService` | **新增**: 日志轮询同步、聚合、余额扣减 |

### Controller（3个新增 + 1个修改）

| 类名 | 操作 |
|------|------|
| `RelayStationController` | **修改**: 添加 list/keys 端点 |
| `PaymentController` | **新增**: 充值相关端点 |
| `DashboardController` | **新增**: 看板相关端点 |
| `SessionController` | **新增**: 客户端会话管理端点 |

---

## 附录 C: 配置新增项

```yaml
# application.yml 新增配置

# new-api 日志同步
newapi:
  url: https://fane.kai.com
  quota-per-unit: 500000

# 支付配置
payment:
  epay:
    enabled: true
    pid: ${EPAY_PID:}
    key: ${EPAY_KEY:}
    api-url: https://epay.example.com
    notify-url: https://kod.kai.com/api/payment/epay/notify
    return-url: https://kod.kai.com/wallet
  stripe:
    enabled: false
    api-secret: ${STRIPE_API_SECRET:}
    webhook-secret: ${STRIPE_WEBHOOK_SECRET:}
  pay-methods:
    - name: 支付宝
      type: alipay
      color: "#1677FF"
      min_topup: 1
    - name: 微信支付
      type: wxpay
      color: "#07C160"
      min_topup: 1
  min-topup: 1
  amount-options: [1, 5, 10, 20, 50, 100]
```

---

> 📅 文档版本: v1.0  
> 📅 创建日期: 2026-07-31  
> 📅 基于PRD: 2026-07-31
