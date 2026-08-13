-- =============================================================
-- FishingTime 数据库初始化脚本
-- 使用前请先创建数据库: CREATE DATABASE fishingtime;
-- =============================================================

CREATE DATABASE IF NOT EXISTS fishingtime DEFAULT CHARSET utf8mb4;
USE fishingtime;

-- ---------------------------------------------------------
-- 用户表
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user` (
    `id`          BIGINT          PRIMARY KEY AUTO_INCREMENT,
    `username`    VARCHAR(32)     NOT NULL                COMMENT '用户名',
    `password`    VARCHAR(255)    NOT NULL                COMMENT 'BCrypt 哈希',
    `nickname`    VARCHAR(64)     NOT NULL                COMMENT '昵称',
    `email`       VARCHAR(128)    DEFAULT NULL            COMMENT '邮箱',
    `openid`      VARCHAR(64)     DEFAULT NULL            COMMENT '微信小程序 OpenID',
    `avatar_url`  VARCHAR(512)    DEFAULT NULL            COMMENT '头像地址',
    `status`      TINYINT         NOT NULL DEFAULT 1      COMMENT '1=正常 0=禁用',
    `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_openid` (`openid`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ---------------------------------------------------------
-- 选颜色最高分表（每用户一行，仿 game_2048_score）
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS `color_focus_score` (
    `id`                BIGINT          PRIMARY KEY AUTO_INCREMENT,
    `user_id`           BIGINT          NOT NULL                COMMENT '关联 user.id，一人一行',
    `best_score`        INT             NOT NULL DEFAULT 0      COMMENT '最高综合得分（排行榜依据，只增不减）',
    `best_accuracy`     DECIMAL(4,2)    DEFAULT NULL            COMMENT '最佳正确率 0.00~1.00（≥10题才更新）',
    `best_avg_reaction`    DECIMAL(5,2)    DEFAULT NULL            COMMENT '最佳平均反应时间（秒，≥10题才更新）',
    `best_switch_accuracy` DECIMAL(4,2)    DEFAULT NULL            COMMENT '最佳规则切换正确率 0.00~1.00（≥10题才更新）',
    `max_streak`        INT             DEFAULT NULL            COMMENT '最高连对',
    `achieved_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近刷分时间',
    `created_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY `uk_user_id` (`user_id`),
    KEY `idx_best_score` (`best_score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='选颜色最高分表';

-- ---------------------------------------------------------
-- 方向陷阱最高分表（每用户一行，字段比选颜色多"规则切换正确率"）
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS `direction_trap_score` (
    `id`                   BIGINT          PRIMARY KEY AUTO_INCREMENT,
    `user_id`              BIGINT          NOT NULL                COMMENT '关联 user.id，一人一行',
    `best_score`           INT             NOT NULL DEFAULT 0      COMMENT '最高综合得分（排行榜依据，只增不减）',
    `best_accuracy`        DECIMAL(4,2)    DEFAULT NULL            COMMENT '最佳正确率 0.00~1.00（≥10题才更新）',
    `best_avg_reaction`    DECIMAL(5,2)    DEFAULT NULL            COMMENT '最佳平均反应时间（秒，≥10题才更新）',
    `best_switch_accuracy` DECIMAL(4,2)    DEFAULT NULL            COMMENT '最佳规则切换正确率 0.00~1.00（≥10题才更新）',
    `max_streak`           INT             DEFAULT NULL            COMMENT '最高连对',
    `achieved_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近刷分时间',
    `created_at`           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY `uk_user_id` (`user_id`),
    KEY `idx_best_score` (`best_score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='方向陷阱最高分表';

-- ---------------------------------------------------------
-- 颜色猎手最佳成绩表（每用户一行，时间制：越小越好）
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS `color_hunter_score` (
    `id`                 BIGINT       PRIMARY KEY AUTO_INCREMENT,
    `user_id`            BIGINT       NOT NULL               COMMENT '关联 user.id，一人一行',
    `best_final_time`    INT          NOT NULL DEFAULT 0     COMMENT '最佳最终成绩（毫秒，越小越好，排行榜依据）',
    `best_actual_time`   INT          DEFAULT NULL           COMMENT '最佳实际用时（毫秒）',
    `lowest_error_count` INT          DEFAULT NULL           COMMENT '最少错误次数',
    `fastest_round`      INT          DEFAULT NULL           COMMENT '最快一轮用时（毫秒）',
    `achieved_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近刷分时间',
    `created_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY `uk_user_id` (`user_id`),
    KEY `idx_best_final_time` (`best_final_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='颜色猎手最佳成绩表';

-- ---------------------------------------------------------
-- 用户反馈表
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS `feedback` (
    `id`         BIGINT       PRIMARY KEY AUTO_INCREMENT,
    `user_id`    BIGINT       DEFAULT NULL        COMMENT '关联 user.id（游客为 NULL）',
    `content`    VARCHAR(500) NOT NULL            COMMENT '反馈内容',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户反馈表';

-- ---------------------------------------------------------
-- 鱼群突围最佳成绩表（每用户一行）
-- 排行规则：best_cleared_pools 降序 → best_released_fish 降序 → 昵称升序
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS `fish_breakout_score` (
    `id`                  BIGINT      PRIMARY KEY AUTO_INCREMENT,
    `user_id`             BIGINT      NOT NULL             COMMENT '关联 user.id，一人一行',
    `best_cleared_pools`  INT         NOT NULL DEFAULT 0   COMMENT '最高清空池数（排行第一依据）',
    `best_released_fish`  INT         NOT NULL DEFAULT 0   COMMENT '最佳记录放生鱼总数（排行第二依据）',
    `best_mistakes`       INT         NOT NULL DEFAULT 0   COMMENT '最佳记录失误数（展示用，不参与排行）',
    `best_duration`       INT         DEFAULT NULL         COMMENT '最佳记录总用时（毫秒，展示用）',
    `achieved_at`         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近一次刷新最佳的时间',
    `created_at`          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY `uk_user_id` (`user_id`),
    KEY `idx_best_cleared_pools` (`best_cleared_pools`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鱼群突围最佳成绩表（每用户一行）';
