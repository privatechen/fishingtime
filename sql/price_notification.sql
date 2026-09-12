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
    PRIMARY KEY (id),
    UNIQUE KEY uk_watch_history_type (watch_id, price_history_id, notification_type),
    KEY idx_user_unread (user_id, is_read, created_at),
    KEY idx_watch_id (watch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PriceGuard in-app price notifications';
