USE fishingtime;

CREATE TABLE IF NOT EXISTS `game_memory_score` (
    `id`                BIGINT      PRIMARY KEY AUTO_INCREMENT,
    `user_id`           BIGINT      NOT NULL             COMMENT '关联 user.id，一人一行',
    `best_passed_count` INT         NOT NULL DEFAULT 0   COMMENT '历史最多过关数，排行榜主指标',
    `best_streak`       INT         NOT NULL DEFAULT 0   COMMENT '该最佳记录对应的最长连续答对次数，排行榜次指标',
    `max_stage`         TINYINT     NOT NULL DEFAULT 1   COMMENT '最高到达难度 1~9，仅展示：3x3替换到5x5混合',
    `achieved_at`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近刷新最佳成绩的时间',
    `created_at`        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY `uk_game_memory_user_id` (`user_id`),
    KEY `idx_game_memory_rank` (`best_passed_count`, `best_streak`, `achieved_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='过不了4关最佳成绩表（每用户一行）';
