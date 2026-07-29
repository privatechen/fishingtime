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
    `avatar_url`  VARCHAR(512)    DEFAULT NULL            COMMENT '头像地址',
    `status`      TINYINT         NOT NULL DEFAULT 1      COMMENT '1=正常 0=禁用',
    `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
