package com.fishingtime.pricewatch.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PriceWatchMapper {

    @Insert("INSERT INTO price_watch (user_id, platform, product_id, purchase_price, start_at, end_at, status) " +
            "VALUES (#{userId}, #{platform}, #{productId}, #{purchasePrice}, #{startAt}, #{endAt}, 1)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PriceWatchInsertParam param);

    /**
     * PriceGuard submissions are idempotent for the same user/platform/product.
     * LAST_INSERT_ID(id) also returns the existing watch id on the update path.
     */
    @Insert("INSERT INTO price_watch (user_id, platform, product_id, purchase_price, start_at, end_at, status) " +
            "VALUES (#{userId}, #{platform}, #{productId}, #{purchasePrice}, #{startAt}, #{endAt}, 1) " +
            "ON DUPLICATE KEY UPDATE purchase_price = VALUES(purchase_price), " +
            "start_at = VALUES(start_at), end_at = VALUES(end_at), status = 1, id = LAST_INSERT_ID(id)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int upsertForPriceguard(PriceWatchInsertParam param);

    @Select({
            "SELECT ordered.* FROM (",
            "SELECT pw.id AS watchId, pw.platform AS platform, ",
            "CASE WHEN pw.platform = 'JD' THEN jp.sku_id ELSE tp.item_id END AS platformProductId, ",
            "CASE WHEN pw.platform = 'JD' THEN jp.product_url ELSE tp.product_url END AS productUrl, ",
            "CASE WHEN pw.platform = 'JD' THEN jp.image_url ELSE tp.image_url END AS imageUrl, ",
            "CASE WHEN pw.platform = 'JD' THEN jp.title ELSE tp.title END AS title, ",
            "(SELECT ph.price FROM price_history ph ",
            " WHERE ph.platform = pw.platform AND ph.product_id = pw.product_id ",
            " AND ph.checked_at >= pw.start_at AND ph.checked_at <= pw.end_at ",
            " ORDER BY ph.checked_at DESC, ph.id DESC LIMIT 1) AS currentPrice, ",
            "pw.purchase_price AS purchasePrice, ",
            "(SELECT ph.price FROM price_history ph ",
            " WHERE ph.platform = pw.platform AND ph.product_id = pw.product_id ",
            " AND ph.checked_at >= pw.start_at AND ph.checked_at <= pw.end_at ",
            " ORDER BY ph.checked_at ASC, ph.id ASC LIMIT 1) AS firstPrice, ",
            "(SELECT ph.checked_at FROM price_history ph ",
            " WHERE ph.platform = pw.platform AND ph.product_id = pw.product_id ",
            " AND ph.checked_at >= pw.start_at AND ph.checked_at <= pw.end_at ",
            " ORDER BY ph.checked_at ASC, ph.id ASC LIMIT 1) AS firstCheckedAt, ",
            "(SELECT ph.price FROM price_history ph ",
            " WHERE ph.platform = pw.platform AND ph.product_id = pw.product_id ",
            " AND ph.checked_at >= pw.start_at AND ph.checked_at <= pw.end_at ",
            " ORDER BY ph.price ASC, ph.checked_at ASC, ph.id ASC LIMIT 1) AS lowestPrice, ",
            "(SELECT ph.checked_at FROM price_history ph ",
            " WHERE ph.platform = pw.platform AND ph.product_id = pw.product_id ",
            " AND ph.checked_at >= pw.start_at AND ph.checked_at <= pw.end_at ",
            " ORDER BY ph.price ASC, ph.checked_at ASC, ph.id ASC LIMIT 1) AS lowestPriceAt, ",
            "(SELECT COUNT(*) FROM price_history ph ",
            " WHERE ph.platform = pw.platform AND ph.product_id = pw.product_id ",
            " AND ph.checked_at >= pw.start_at AND ph.checked_at <= pw.end_at ",
            " AND ph.price < pw.purchase_price ",
            " AND (NOT EXISTS (",
            "   SELECT 1 FROM price_history previous ",
            "   WHERE previous.platform = ph.platform AND previous.product_id = ph.product_id ",
            "   AND previous.checked_at >= pw.start_at AND previous.checked_at <= pw.end_at ",
            "   AND (previous.checked_at < ph.checked_at OR (previous.checked_at = ph.checked_at AND previous.id < ph.id))",
            " ) OR (",
            "   SELECT previous.price FROM price_history previous ",
            "   WHERE previous.platform = ph.platform AND previous.product_id = ph.product_id ",
            "   AND previous.checked_at >= pw.start_at AND previous.checked_at <= pw.end_at ",
            "   AND (previous.checked_at < ph.checked_at OR (previous.checked_at = ph.checked_at AND previous.id < ph.id)) ",
            "   ORDER BY previous.checked_at DESC, previous.id DESC LIMIT 1",
            " ) >= pw.purchase_price)",
            ") AS lowPriceEventCount, ",
            "pw.start_at AS startAt, pw.end_at AS endAt, ",
            "CAST(GREATEST(CEIL(TIMESTAMPDIFF(SECOND, NOW(), pw.end_at) / 86400.0), 0) AS SIGNED) AS remainingDays, ",
            "pw.status AS status, ",
            "CASE WHEN pw.platform = 'JD' THEN jp.status ELSE tp.status END AS productStatus ",
            "FROM price_watch pw ",
            "LEFT JOIN jd_product jp ON pw.platform = 'JD' AND jp.id = pw.product_id ",
            "LEFT JOIN taobao_product tp ON pw.platform = 'TAOBAO' AND tp.id = pw.product_id ",
            "WHERE pw.user_id = #{userId} AND pw.status = 1 ",
            ") ordered ",
            "ORDER BY CASE WHEN ordered.currentPrice IS NULL THEN 1 ELSE 0 END ASC, ",
            "(ordered.purchasePrice - ordered.currentPrice) DESC, ",
            "ordered.remainingDays ASC, ordered.watchId DESC"
    })
    List<PriceWatchListRow> findByUserId(@Param("userId") Long userId);

    @Select({
            "SELECT COUNT(*) AS totalWatchCount, ",
            "COALESCE(SUM(summary_items.lowPriceEventCount), 0) AS lowPriceEventCount, ",
            "COALESCE(SUM(summary_items.maxDifference), 0) AS cumulativeDifference ",
            "FROM (",
            " SELECT pw.id, ",
            " (SELECT COUNT(*) FROM price_history ph ",
            "  WHERE ph.platform = pw.platform AND ph.product_id = pw.product_id ",
            "  AND ph.checked_at >= pw.start_at AND ph.checked_at <= pw.end_at ",
            "  AND ph.price < pw.purchase_price ",
            "  AND (NOT EXISTS (",
            "    SELECT 1 FROM price_history previous ",
            "    WHERE previous.platform = ph.platform AND previous.product_id = ph.product_id ",
            "    AND previous.checked_at >= pw.start_at AND previous.checked_at <= pw.end_at ",
            "    AND (previous.checked_at < ph.checked_at OR (previous.checked_at = ph.checked_at AND previous.id < ph.id))",
            "  ) OR (",
            "    SELECT previous.price FROM price_history previous ",
            "    WHERE previous.platform = ph.platform AND previous.product_id = ph.product_id ",
            "    AND previous.checked_at >= pw.start_at AND previous.checked_at <= pw.end_at ",
            "    AND (previous.checked_at < ph.checked_at OR (previous.checked_at = ph.checked_at AND previous.id < ph.id)) ",
            "    ORDER BY previous.checked_at DESC, previous.id DESC LIMIT 1",
            "  ) >= pw.purchase_price)",
            " ) AS lowPriceEventCount, ",
            " GREATEST(pw.purchase_price - COALESCE((",
            "   SELECT MIN(ph2.price) FROM price_history ph2 ",
            "   WHERE ph2.platform = pw.platform AND ph2.product_id = pw.product_id ",
            "   AND ph2.checked_at >= pw.start_at AND ph2.checked_at <= pw.end_at",
            " ), pw.purchase_price), 0) AS maxDifference ",
            " FROM price_watch pw WHERE pw.user_id = #{userId}",
            ") summary_items"
    })
    PriceWatchSummaryRow findSummaryByUserId(@Param("userId") Long userId);

    @Select("SELECT platform, product_id AS productId, start_at AS startAt, end_at AS endAt " +
            "FROM price_watch WHERE id = #{watchId} AND user_id = #{userId} LIMIT 1")
    PriceWatchTarget findTargetByWatchAndUser(@Param("watchId") Long watchId, @Param("userId") Long userId);

    @Update("UPDATE price_watch SET status = 0 WHERE id = #{watchId} AND user_id = #{userId} AND status = 1")
    int disableByUser(@Param("watchId") Long watchId, @Param("userId") Long userId);

    class PriceWatchInsertParam {
        private Long id;
        private Long userId;
        private String platform;
        private Long productId;
        private BigDecimal purchasePrice;
        private LocalDateTime startAt;
        private LocalDateTime endAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getPlatform() { return platform; }
        public void setPlatform(String platform) { this.platform = platform; }
        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public BigDecimal getPurchasePrice() { return purchasePrice; }
        public void setPurchasePrice(BigDecimal purchasePrice) { this.purchasePrice = purchasePrice; }
        public LocalDateTime getStartAt() { return startAt; }
        public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }
        public LocalDateTime getEndAt() { return endAt; }
        public void setEndAt(LocalDateTime endAt) { this.endAt = endAt; }
    }

    class PriceWatchTarget {
        private String platform;
        private Long productId;
        private LocalDateTime startAt;
        private LocalDateTime endAt;

        public String getPlatform() { return platform; }
        public void setPlatform(String platform) { this.platform = platform; }
        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public LocalDateTime getStartAt() { return startAt; }
        public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }
        public LocalDateTime getEndAt() { return endAt; }
        public void setEndAt(LocalDateTime endAt) { this.endAt = endAt; }
    }

    class PriceWatchSummaryRow {
        private Integer totalWatchCount;
        private Integer lowPriceEventCount;
        private BigDecimal cumulativeDifference;

        public Integer getTotalWatchCount() { return totalWatchCount; }
        public void setTotalWatchCount(Integer totalWatchCount) { this.totalWatchCount = totalWatchCount; }
        public Integer getLowPriceEventCount() { return lowPriceEventCount; }
        public void setLowPriceEventCount(Integer lowPriceEventCount) { this.lowPriceEventCount = lowPriceEventCount; }
        public BigDecimal getCumulativeDifference() { return cumulativeDifference; }
        public void setCumulativeDifference(BigDecimal cumulativeDifference) { this.cumulativeDifference = cumulativeDifference; }
    }

    class PriceWatchListRow {
        private Long watchId;
        private String platform;
        private String platformProductId;
        private String productUrl;
        private String imageUrl;
        private String title;
        private BigDecimal currentPrice;
        private BigDecimal purchasePrice;
        private BigDecimal firstPrice;
        private LocalDateTime firstCheckedAt;
        private BigDecimal lowestPrice;
        private LocalDateTime lowestPriceAt;
        private Integer lowPriceEventCount;
        private LocalDateTime startAt;
        private LocalDateTime endAt;
        private Integer remainingDays;
        private Integer status;
        private Integer productStatus;

        public Long getWatchId() { return watchId; }
        public void setWatchId(Long watchId) { this.watchId = watchId; }
        public String getPlatform() { return platform; }
        public void setPlatform(String platform) { this.platform = platform; }
        public String getPlatformProductId() { return platformProductId; }
        public void setPlatformProductId(String platformProductId) { this.platformProductId = platformProductId; }
        public String getProductUrl() { return productUrl; }
        public void setProductUrl(String productUrl) { this.productUrl = productUrl; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public BigDecimal getCurrentPrice() { return currentPrice; }
        public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }
        public BigDecimal getPurchasePrice() { return purchasePrice; }
        public void setPurchasePrice(BigDecimal purchasePrice) { this.purchasePrice = purchasePrice; }
        public BigDecimal getFirstPrice() { return firstPrice; }
        public void setFirstPrice(BigDecimal firstPrice) { this.firstPrice = firstPrice; }
        public LocalDateTime getFirstCheckedAt() { return firstCheckedAt; }
        public void setFirstCheckedAt(LocalDateTime firstCheckedAt) { this.firstCheckedAt = firstCheckedAt; }
        public BigDecimal getLowestPrice() { return lowestPrice; }
        public void setLowestPrice(BigDecimal lowestPrice) { this.lowestPrice = lowestPrice; }
        public LocalDateTime getLowestPriceAt() { return lowestPriceAt; }
        public void setLowestPriceAt(LocalDateTime lowestPriceAt) { this.lowestPriceAt = lowestPriceAt; }
        public Integer getLowPriceEventCount() { return lowPriceEventCount; }
        public void setLowPriceEventCount(Integer lowPriceEventCount) { this.lowPriceEventCount = lowPriceEventCount; }
        public LocalDateTime getStartAt() { return startAt; }
        public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }
        public LocalDateTime getEndAt() { return endAt; }
        public void setEndAt(LocalDateTime endAt) { this.endAt = endAt; }
        public Integer getRemainingDays() { return remainingDays; }
        public void setRemainingDays(Integer remainingDays) { this.remainingDays = remainingDays; }
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
        public Integer getProductStatus() { return productStatus; }
        public void setProductStatus(Integer productStatus) { this.productStatus = productStatus; }
    }
}
