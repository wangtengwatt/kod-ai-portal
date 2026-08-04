# 看板聚合表设计

> 数据来源：`api_request_logs`（从 new-api `/api/log/token` 同步来的原始日志）
>
> 参考：new-api 的 `quota_data` 表设计（`model/usedata.go`），但按单人单 Token 场景精简。
>
> 核心思路：不直接查原始日志做 SUM → 而是定时把原始日志聚合到看板表里，看板直接查聚合表。

---

## 总体数据流

```
api_request_logs (原始日志，你同步来的)
    │
    │  定时 SQL（每次同步后跑一次）
    ▼
┌──────────────────────────┐
│  dashboard_hourly        │  ← 按模型+小时聚合，等效 new-api 的 quota_data
│  dashboard_model_summary │  ← 按模型汇总，看板卡片用
└──────────────────────────┘
    │
    │  看板 API 直接查这两张表
    ▼
  前端图表
```

---

## 一、dashboard_hourly — 按模型+小时的用量明细

等效 new-api 的 `quota_data`，但精简了多用户/多 Token 维度。

```sql
CREATE TABLE dashboard_hourly (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',

    -- 维度
    model_name      VARCHAR(128) NOT NULL COMMENT '模型名称',
    hour_bucket     BIGINT       NOT NULL COMMENT '小时桶，Unix秒（created_at 整除 3600）',
    channel_id      INT          NOT NULL DEFAULT 0 COMMENT '渠道ID',

    -- 聚合值
    request_count   INT          NOT NULL DEFAULT 0 COMMENT '请求次数',
    quota           INT          NOT NULL DEFAULT 0 COMMENT '配额消耗（合计）',
    prompt_tokens   INT          NOT NULL DEFAULT 0 COMMENT '提示词Token数（合计）',
    completion_tokens INT        NOT NULL DEFAULT 0 COMMENT '补全Token数（合计）',
    token_used      INT          NOT NULL DEFAULT 0 COMMENT 'Token总用量（prompt+completion）',
    use_time        INT          NOT NULL DEFAULT 0 COMMENT '请求总耗时，秒',
    stream_count    INT          NOT NULL DEFAULT 0 COMMENT '流式请求次数',

    -- 去重
    UNIQUE KEY uk_model_hour_channel (model_name, hour_bucket, channel_id),

    -- 查询
    INDEX idx_hour_bucket (hour_bucket),
    INDEX idx_model_name (model_name)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='按模型+小时聚合的用量明细（等效new-api quota_data）';
```

### 聚合 SQL（从 api_request_logs 增量写入）

每次同步完 `api_request_logs` 后执行，按 `synced_at > 上次聚合时间` 取本轮新日志：

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
WHERE type = 2                          -- 只统计消费日志
  AND model_name != ''                  -- 过滤空模型
  AND synced_at > ?                     -- 本批次的新数据
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

### 看板查询示例

```sql
-- 时间趋势图：某模型最近 24 小时的用量走势
SELECT hour_bucket,
       FROM_UNIXTIME(hour_bucket) AS time_label,
       quota,
       request_count,
       token_used
FROM dashboard_hourly
WHERE model_name = 'gpt-4'
  AND hour_bucket >= UNIX_TIMESTAMP() - 86400
ORDER BY hour_bucket ASC;

-- 模型用量排行（所有模型、指定时间范围）
SELECT model_name,
       SUM(request_count) AS total_requests,
       SUM(quota)         AS total_quota,
       SUM(prompt_tokens) AS total_prompt,
       SUM(completion_tokens) AS total_completion,
       SUM(token_used)    AS total_tokens
FROM dashboard_hourly
WHERE hour_bucket >= ? AND hour_bucket <= ?
GROUP BY model_name
ORDER BY total_quota DESC;

-- 渠道分布（饼图用）
SELECT channel_id,
       SUM(quota) AS quota
FROM dashboard_hourly
WHERE hour_bucket >= ? AND hour_bucket <= ?
GROUP BY channel_id;
```

---

## 二、dashboard_model_summary — 按模型汇总（看板卡片用）

等效 new-api 的 `RankingQuotaTotal`，查 `quota_data` 做 `SUM(token_used) GROUP BY model_name`。

```sql
CREATE TABLE dashboard_model_summary (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',

    model_name      VARCHAR(128) NOT NULL COMMENT '模型名称',

    -- 累计值（从开始到现在的总和）
    total_requests  INT          NOT NULL DEFAULT 0 COMMENT '总请求次数',
    total_quota     INT          NOT NULL DEFAULT 0 COMMENT '总配额消耗',
    total_prompt    INT          NOT NULL DEFAULT 0 COMMENT '总提示词Token数',
    total_completion INT         NOT NULL DEFAULT 0 COMMENT '总补全Token数',
    total_tokens    INT          NOT NULL DEFAULT 0 COMMENT '总Token用量',
    total_use_time  INT          NOT NULL DEFAULT 0 COMMENT '总耗时，秒',
    total_stream    INT          NOT NULL DEFAULT 0 COMMENT '总流式请求次数',

    -- 最近一次更新时间
    last_request_at BIGINT       NOT NULL DEFAULT 0 COMMENT '最近一次请求时间，Unix秒',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_model (model_name)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='按模型汇总（累计值，看板卡片）；等效new-api RankingQuotaTotal';
```

### 聚合 SQL（增量更新累计值）

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
  AND synced_at > ?                   -- 本批次新数据
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

### 看板查询示例

```sql
-- 看板卡片：总用量概览
SELECT
    SUM(total_quota)    AS quota_used_all,
    SUM(total_tokens)   AS tokens_all,
    SUM(total_requests) AS requests_all
FROM dashboard_model_summary;

-- 按模型排序（排行榜）
SELECT model_name, total_requests, total_quota, total_tokens
FROM dashboard_model_summary
ORDER BY total_quota DESC;
```

---

## 三、可选：dashboard_daily — 按天汇总

如果你的用户量变大，hourly 表会变多，可以再加一层 daily 聚合。

```sql
CREATE TABLE dashboard_daily (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    model_name      VARCHAR(128) NOT NULL,
    day_bucket      BIGINT       NOT NULL COMMENT '天桶，Unix秒（整除 86400）',
    channel_id      INT          NOT NULL DEFAULT 0,

    request_count   INT          NOT NULL DEFAULT 0,
    quota           INT          NOT NULL DEFAULT 0,
    prompt_tokens   INT          NOT NULL DEFAULT 0,
    completion_tokens INT        NOT NULL DEFAULT 0,
    token_used      INT          NOT NULL DEFAULT 0,
    use_time        INT          NOT NULL DEFAULT 0,

    UNIQUE KEY uk_model_day_channel (model_name, day_bucket, channel_id),
    INDEX idx_day_bucket (day_bucket)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='按模型+天聚合的用量明细';
```

聚合 SQL 和 hourly 一样，只是 `hour_bucket` 替换成 `created_at - (created_at % 86400)`。

---

## 四、完整建表脚本（一次性执行）

```sql
-- ====================================================
-- new-api 看板聚合表（你的数据库）
-- 配套 LogPoller 同步方案使用
-- ====================================================

-- 1. 原始日志表（轮询同步写入）
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
    other               TEXT         COMMENT '扩展JSON',
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

-- 2. 按模型+小时聚合（等效 new-api quota_data）
CREATE TABLE dashboard_hourly (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    model_name        VARCHAR(128) NOT NULL,
    hour_bucket       BIGINT       NOT NULL COMMENT 'Unix秒，整除3600',
    channel_id        INT          NOT NULL DEFAULT 0,
    request_count     INT          NOT NULL DEFAULT 0,
    quota             INT          NOT NULL DEFAULT 0,
    prompt_tokens     INT          NOT NULL DEFAULT 0,
    completion_tokens INT          NOT NULL DEFAULT 0,
    token_used        INT          NOT NULL DEFAULT 0 COMMENT 'prompt+completion',
    use_time          INT          NOT NULL DEFAULT 0,
    stream_count      INT          NOT NULL DEFAULT 0,
    UNIQUE KEY uk_model_hour_channel (model_name, hour_bucket, channel_id),
    INDEX idx_hour_bucket (hour_bucket),
    INDEX idx_model_name (model_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='按模型+小时聚合（等效new-api quota_data）';

-- 3. 按模型汇总累计（看板卡片）
CREATE TABLE dashboard_model_summary (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    model_name        VARCHAR(128) NOT NULL,
    total_requests    INT          NOT NULL DEFAULT 0,
    total_quota       INT          NOT NULL DEFAULT 0,
    total_prompt      INT          NOT NULL DEFAULT 0,
    total_completion  INT          NOT NULL DEFAULT 0,
    total_tokens      INT          NOT NULL DEFAULT 0,
    total_use_time    INT          NOT NULL DEFAULT 0,
    total_stream      INT          NOT NULL DEFAULT 0,
    last_request_at   BIGINT       NOT NULL DEFAULT 0 COMMENT '最近一次请求时间',
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_model (model_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='按模型汇总累计（看板卡片）';
```

---

## 五、增量聚合流程

每次 `LogPoller` 同步完 `api_request_logs` 后，执行以下步骤：

```
1. 记录聚合起点: last_agg_at = 从配置或状态表读取

2. 聚合 dashboard_hourly
   INSERT INTO dashboard_hourly (...) SELECT ... FROM api_request_logs
   WHERE type = 2 AND model_name != '' AND synced_at > last_agg_at
   GROUP BY model_name, hour_bucket, channel_id
   ON DUPLICATE KEY UPDATE ...

3. 聚合 dashboard_model_summary
   INSERT INTO dashboard_model_summary (...) SELECT ... FROM api_request_logs
   WHERE type = 2 AND model_name != '' AND synced_at > last_agg_at
   GROUP BY model_name
   ON DUPLICATE KEY UPDATE ...

4. 更新聚合水位: UPDATE agg_state SET last_agg_at = NOW()

5. 返回给前端: 本次聚合新增了多少条
```

三个聚合 SQL 从上文第 1、2 节直接取用。

---

## 六、new-api 对照表

| new-api | 你的数据库 | 说明 |
|---|---|---|
| `logs` | `api_request_logs` | 原始日志（从 `/api/log/token` 同步） |
| `quota_data` | `dashboard_hourly` | 按 模型+小时+渠道 聚合 |
| `RankingQuotaTotal`（查询结果） | `dashboard_model_summary` | 按模型累计汇总 |
| `RankingQuotaBucket`（查询结果） | 直接从 `dashboard_hourly` 查 | 按时间桶展开就行 |
| `FlowQuotaData`（查询结果） | 直接从 `dashboard_hourly` 查 | 按 token_id / channel_id 分组 |
| `/api/data/*`（看板接口） | 你的后端接口自己写 | 查 `dashboard_hourly` + `dashboard_model_summary` |

---

## 七、前端看板对应的 SQL

```sql
-- ==================== 卡片：总览 ====================
SELECT
    SUM(total_quota)   AS quota_used_all,
    SUM(total_tokens)  AS tokens_all,
    SUM(total_requests) AS requests_all,
    SUM(total_use_time) AS use_time_all_sec
FROM dashboard_model_summary;

-- ==================== 模型用量排行榜 ====================
SELECT model_name, total_quota, total_tokens, total_requests,
       last_request_at
FROM dashboard_model_summary
WHERE total_quota > 0
ORDER BY total_quota DESC
LIMIT 20;

-- ==================== 时间趋势图（按模型，支持多模型叠加） ====================
SELECT model_name, hour_bucket,
       FROM_UNIXTIME(hour_bucket, '%m-%d %H:00') AS label,
       quota, request_count, token_used
FROM dashboard_hourly
WHERE model_name IN ('gpt-4', 'gpt-4o', 'claude-sonnet-5')
  AND hour_bucket >= UNIX_TIMESTAMP() - 86400
ORDER BY hour_bucket ASC;

-- ==================== 渠道占比（饼图） ====================
SELECT channel_id,
       SUM(quota) AS quota
FROM dashboard_hourly
WHERE hour_bucket >= ? AND hour_bucket <= ?
GROUP BY channel_id
ORDER BY quota DESC;

-- ==================== 最近请求记录（查原始表） ====================
SELECT request_id, created_at, model_name, quota,
       prompt_tokens, completion_tokens, use_time, is_stream
FROM api_request_logs
WHERE type = 2
ORDER BY created_at DESC
LIMIT 50;
```

---

## 八、为什么不直接查 `api_request_logs` SUM？

| 方式 | 1 万条日志 | 100 万条日志 | 说明 |
|---|---|---|---|
| 直接 `SUM` | ~10ms | ~500ms+ | 数据越多越慢，每次看板刷新都扫全表 |
| 查 `dashboard_hourly` | ~1ms | ~5ms | 行数少（一个模型一天才 24 行），有索引丝滑 |
| 查 `dashboard_model_summary` | ~0.5ms | ~0.5ms | 每个模型就一行 |

> 📅 2026-07-31
