CREATE TABLE IF NOT EXISTS price_notification (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    watch_id BIGINT NOT NULL,
    price_history_id BIGINT NOT NULL,
    notification_type VARCHAR(32) NOT NULL COMMENT 'BELOW_PURCHASE or BELOW_PREVIOUS',
    product_title VARCHAR(255) NULL,
    current_price DECIMAL(10,2) NOT NULL,
    comparison_price DECIMAL(10,2) NOT NULL,
    drop_amount DECIMAL(10,2) NOT NULL,
    is_read TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at DATETIME NULL,
    wechat_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/FAILED',
    wechat_sent_at DATETIME NULL,
    wechat_error VARCHAR(255) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_watch_history_type (watch_id, price_history_id, notification_type),
    KEY idx_user_unread (user_id, is_read, created_at),
    KEY idx_watch_id (watch_id),
    KEY idx_wechat_pending (wechat_status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PriceGuard in-app price notifications';

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
