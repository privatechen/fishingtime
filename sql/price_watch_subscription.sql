-- PriceGuard 微信一次性订阅消息
-- 模板：商品降价提醒
-- 模板 ID：SH59Aabm4qEo0P2RwMN3cENfCdtxU4bLdG9UJew5ZE4

CREATE TABLE IF NOT EXISTS price_watch_subscription (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    watch_id BIGINT NOT NULL,
    template_id VARCHAR(128) NOT NULL,
    available_count INT NOT NULL DEFAULT 0 COMMENT '尚可发送的一次性订阅消息次数',
    used_count INT NOT NULL DEFAULT 0,
    last_granted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_watch_template (user_id, watch_id, template_id),
    KEY idx_watch_available (watch_id, available_count),
    KEY idx_user_available (user_id, available_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PriceGuard 微信订阅消息授权';

ALTER TABLE price_notification
    ADD COLUMN wechat_status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING/SENT/FAILED',
    ADD COLUMN wechat_sent_at DATETIME NULL,
    ADD COLUMN wechat_error VARCHAR(255) NULL,
    ADD KEY idx_wechat_pending (wechat_status, created_at);
