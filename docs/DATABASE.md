# 数据库设计

> 最后同步：2026-08-05

## 连接信息

- MySQL 8，远端实例 `123.207.213.29:3306`，库名 `fishingtime`（连接串见 `application.yml`）
- ⚠️ 凭据明文存于 `application.yml` / `application-local.yml`，仅限学习/内网环境

## 表清单

| 表 | 说明 | DDL 位置 |
|----|------|----------|
| `user` | 用户 | ✅ `sql/init.sql` |
| `region_code` | 高德行政区划编码 | ⚠️ 仓库无 DDL，远端手工建 |
| `daily_sentence` | 每日一句 | ⚠️ 仓库无 DDL，远端手工建 |
| `game_2048_score` | 2048 最高分 | ⚠️ 仓库无 DDL，远端手工建 |

> **待办缺口**：后三张表在远端 MySQL 手工创建，`sql/init.sql` 中只有 `user` 表。建议补全三张表的建表脚本，保证新环境可一键初始化。

---

## user — 用户表

```sql
CREATE TABLE IF NOT EXISTS `user` (
    `id`          BIGINT          PRIMARY KEY AUTO_INCREMENT,
    `username`    VARCHAR(32)     NOT NULL                COMMENT '用户名',
    `password`    VARCHAR(255)    NOT NULL                COMMENT 'BCrypt 哈希',
    `nickname`    VARCHAR(64)     NOT NULL                COMMENT '昵称',
    `email`       VARCHAR(128)    DEFAULT NULL            COMMENT '邮箱',
    `avatar_url`  VARCHAR(512)    DEFAULT NULL            COMMENT '头像地址',
    `status`      TINYINT         NOT NULL DEFAULT 1      COMMENT '1=正常 0=禁用',
    `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
```

| 索引 | 类型 | 说明 |
|------|------|------|
| uk_username | UNIQUE | 用户名唯一 |
| idx_created_at | INDEX | 按创建时间排序查询 |

`user` 表被 `game_2048_score`、热榜排行榜（JOIN 反查昵称）引用。

---

## region_code — 行政区划编码表

> ⚠️ 仓库无 DDL。以下字段根据 `Region` 实体 + `RegionMapper.xml` 还原，字段清单可信，具体类型/DEFAULT 以远端库为准。

| 字段 | 说明 |
|------|------|
| `id` | 主键自增 |
| `name` | 地区名称 |
| `adcode` | 高德行政区划编码 |
| `citycode` | 高德城市编码（可空） |
| `level` | 行政级别（由 `AdcodeParser` 按编码推断） |
| `parent_adcode` | 父级编码（`children/{adcode}` 查询依据，省/直辖市为空） |
| `created_at` / `updated_at` | 时间戳 |

建议索引：
- `adcode` UNIQUE（按编码查询）
- `parent_adcode` INDEX（`children` 查询）

数据来源：`方案/AMap_adcode_citycode_20210406.xlsx` → `convert_region_json.py` 转换 → `POST /api/region/import` 批量导入。

---

## daily_sentence — 每日一句表

> ⚠️ 仓库无 DDL。以下字段根据 `DailySentence` 实体 + `DailySentenceMapper.xml` 还原。

| 字段 | 说明 |
|------|------|
| `id` | 主键自增 |
| `content` | 句子内容 |
| `category` | 分类（当前接口未返回） |
| `enabled` | 1=启用 0=禁用（接口只取启用） |
| `created_at` / `updated_at` | 时间戳 |

业务逻辑（`DailySentenceServiceImpl`）：
- 启动时把启用句子一次性加载到内存，接口从内存读取
- 「今日一句」：按日期取模选一条，同一自然日固定，跨天自动切换
- 数据库为空时返回默认文案「今天也要开心一点～」

---

## game_2048_score — 2048 最高分表

> ⚠️ 仓库无 DDL。以下字段根据 `Game2048Score` 实体 + `Game2048ScoreMapper.xml` 还原。

| 字段 | 说明 |
|------|------|
| `id` | 主键自增 |
| `user_id` | 关联 `user.id`（一人一条记录） |
| `best_score` | 最高分（只增不减） |
| `max_tile` | 达到的最大方块 |
| `achieved_at` | 达成时间 |
| `created_at` / `updated_at` | 时间戳 |

查询逻辑：
- 排行榜：`ORDER BY best_score DESC, achieved_at ASC LIMIT 20`，LEFT JOIN `user` 取昵称
- 首次提交 insert，之后仅更高分时 update（`WHERE user_id=? AND best_score < ?`）

---

## 索引说明（汇总）

| 表 | 索引 | 类型 | 说明 |
|----|------|------|------|
| user | uk_username | UNIQUE | 用户名唯一 |
| user | idx_created_at | INDEX | 按创建时间排序 |
| region_code | adcode（建议） | UNIQUE | 按编码查询 |
| region_code | parent_adcode（建议） | INDEX | 子级查询 |

---

## 后续扩展规划

后续模块预计需要的表：

- 帖子表（post）
- 评论表（comment）
- 点赞表（like）
- 收藏表（favorite）
- 消息通知表（notification）
- 操作日志表（operation_log）

每张表均包含 `id`, `created_at`, `updated_at` 字段。
