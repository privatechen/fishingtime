# 数据库设计

## 当前阶段 — 用户表

```sql
CREATE TABLE `user` (
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

## 索引说明

| 索引 | 类型 | 说明 |
|------|------|------|
| uk_username | UNIQUE | 用户名唯一 |
| idx_created_at | INDEX | 按创建时间排序查询 |

## 后续扩展规划

后续模块预计需要的表：

- 帖子表（post）
- 评论表（comment）
- 点赞表（like）
- 收藏表（favorite）
- 消息通知表（notification）
- 操作日志表（operation_log）

每张表均包含 `id`, `created_at`, `updated_at` 字段。
