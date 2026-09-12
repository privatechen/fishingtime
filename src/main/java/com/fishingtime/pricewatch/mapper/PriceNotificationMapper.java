package com.fishingtime.pricewatch.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PriceNotificationMapper {

    @Insert({
            "INSERT IGNORE INTO price_notification ",
            "(user_id, watch_id, price_history_id, notification_type, product_title, ",
            "current_price, comparison_price, drop_amount, is_read, created_at) ",
            "SELECT pw.user_id, pw.id, ph.id, 'BELOW_PURCHASE', ",
            "CASE WHEN pw.platform = 'JD' THEN jp.title ELSE tp.title END, ",
            "ph.price, pw.purchase_price, pw.purchase_price - ph.price, 0, ph.checked_at ",
            "FROM price_watch pw ",
            "JOIN price_history ph ",
            "ON ph.platform = pw.platform AND ph.product_id = pw.product_id ",
            "LEFT JOIN jd_product jp ON pw.platform = 'JD' AND jp.id = pw.product_id ",
            "LEFT JOIN taobao_product tp ON pw.platform = 'TAOBAO' AND tp.id = pw.product_id ",
            "WHERE pw.status = 1 ",
            "AND ph.checked_at >= pw.start_at ",
            "AND ph.checked_at <= pw.end_at ",
            "AND ph.checked_at >= #{startedAt} ",
            "AND ph.price < pw.purchase_price ",
            "AND ( ",
            "SELECT previous.price ",
            "FROM price_history previous ",
            "WHERE previous.platform = ph.platform ",
            "AND previous.product_id = ph.product_id ",
            "AND (previous.checked_at < ph.checked_at ",
            "OR (previous.checked_at = ph.checked_at AND previous.id < ph.id)) ",
            "ORDER BY previous.checked_at DESC, previous.id DESC ",
            "LIMIT 1 ",
            ") >= pw.purchase_price ",
            "OR ( ",
            "pw.status = 1 ",
            "AND ph.checked_at >= pw.start_at ",
            "AND ph.checked_at <= pw.end_at ",
            "AND ph.checked_at >= #{startedAt} ",
            "AND ph.price < pw.purchase_price ",
            "AND NOT EXISTS ( ",
            "SELECT 1 FROM price_history previous ",
            "WHERE previous.platform = ph.platform ",
            "AND previous.product_id = ph.product_id ",
            "AND (previous.checked_at < ph.checked_at ",
            "OR (previous.checked_at = ph.checked_at AND previous.id < ph.id)) ",
            ") ",
            ")"
    })
    int insertBelowPurchasePriceNotifications(@Param("startedAt") LocalDateTime startedAt);

    @Insert({
            "INSERT IGNORE INTO price_notification ",
            "(user_id, watch_id, price_history_id, notification_type, product_title, ",
            "current_price, comparison_price, drop_amount, is_read, created_at) ",
            "SELECT pw.user_id, pw.id, ph.id, 'BELOW_PREVIOUS', ",
            "CASE WHEN pw.platform = 'JD' THEN jp.title ELSE tp.title END, ",
            "ph.price, ",
            "(SELECT previous.price ",
            "FROM price_history previous ",
            "WHERE previous.platform = ph.platform ",
            "AND previous.product_id = ph.product_id ",
            "AND (previous.checked_at < ph.checked_at ",
            "OR (previous.checked_at = ph.checked_at AND previous.id < ph.id)) ",
            "ORDER BY previous.checked_at DESC, previous.id DESC ",
            "LIMIT 1), ",
            "(SELECT previous.price ",
            "FROM price_history previous ",
            "WHERE previous.platform = ph.platform ",
            "AND previous.product_id = ph.product_id ",
            "AND (previous.checked_at < ph.checked_at ",
            "OR (previous.checked_at = ph.checked_at AND previous.id < ph.id)) ",
            "ORDER BY previous.checked_at DESC, previous.id DESC ",
            "LIMIT 1) - ph.price, ",
            "0, ph.checked_at ",
            "FROM price_watch pw ",
            "JOIN price_history ph ",
            "ON ph.platform = pw.platform AND ph.product_id = pw.product_id ",
            "LEFT JOIN jd_product jp ON pw.platform = 'JD' AND jp.id = pw.product_id ",
            "LEFT JOIN taobao_product tp ON pw.platform = 'TAOBAO' AND tp.id = pw.product_id ",
            "WHERE pw.status = 1 ",
            "AND ph.checked_at >= pw.start_at ",
            "AND ph.checked_at <= pw.end_at ",
            "AND ph.checked_at >= #{startedAt} ",
            "AND ph.price < ( ",
            "SELECT previous.price ",
            "FROM price_history previous ",
            "WHERE previous.platform = ph.platform ",
            "AND previous.product_id = ph.product_id ",
            "AND (previous.checked_at < ph.checked_at ",
            "OR (previous.checked_at = ph.checked_at AND previous.id < ph.id)) ",
            "ORDER BY previous.checked_at DESC, previous.id DESC ",
            "LIMIT 1 ",
            ")"
    })
    int insertBelowPreviousPriceNotifications(@Param("startedAt") LocalDateTime startedAt);

    @Select({
            "SELECT id, watch_id AS watchId, notification_type AS type, ",
            "product_title AS productTitle, current_price AS currentPrice, ",
            "comparison_price AS comparisonPrice, drop_amount AS dropAmount, ",
            "created_at AS createdAt ",
            "FROM price_notification ",
            "WHERE user_id = #{userId} AND is_read = 0 ",
            "ORDER BY created_at DESC, id DESC ",
            "LIMIT 50"
    })
    List<NotificationRow> findUnreadByUser(@Param("userId") Long userId);

    @Update({
            "UPDATE price_notification ",
            "SET is_read = 1, read_at = NOW() ",
            "WHERE id = #{notificationId} AND user_id = #{userId} AND is_read = 0"
    })
    int markRead(@Param("userId") Long userId, @Param("notificationId") Long notificationId);

    class NotificationRow {
        private Long id;
        private Long watchId;
        private String type;
        private String productTitle;
        private BigDecimal currentPrice;
        private BigDecimal comparisonPrice;
        private BigDecimal dropAmount;
        private LocalDateTime createdAt;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getWatchId() { return watchId; }
        public void setWatchId(Long watchId) { this.watchId = watchId; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getProductTitle() { return productTitle; }
        public void setProductTitle(String productTitle) { this.productTitle = productTitle; }
        public BigDecimal getCurrentPrice() { return currentPrice; }
        public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }
        public BigDecimal getComparisonPrice() { return comparisonPrice; }
        public void setComparisonPrice(BigDecimal comparisonPrice) { this.comparisonPrice = comparisonPrice; }
        public BigDecimal getDropAmount() { return dropAmount; }
        public void setDropAmount(BigDecimal dropAmount) { this.dropAmount = dropAmount; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }
}
