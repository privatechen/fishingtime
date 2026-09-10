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

    @Select("SELECT pw.id AS watchId, pw.platform AS platform, " +
            "CASE WHEN pw.platform = 'JD' THEN jp.sku_id ELSE tp.item_id END AS platformProductId, " +
            "CASE WHEN pw.platform = 'JD' THEN jp.product_url ELSE tp.product_url END AS productUrl, " +
            "CASE WHEN pw.platform = 'JD' THEN jp.image_url ELSE tp.image_url END AS imageUrl, " +
            "CASE WHEN pw.platform = 'TAOBAO' THEN tp.title ELSE NULL END AS title, " +
            "CASE WHEN pw.platform = 'JD' THEN jp.current_price ELSE tp.current_price END AS currentPrice, " +
            "pw.purchase_price AS purchasePrice, pw.start_at AS startAt, pw.end_at AS endAt, pw.status AS status, " +
            "CASE WHEN pw.platform = 'JD' THEN jp.status ELSE tp.status END AS productStatus " +
            "FROM price_watch pw " +
            "LEFT JOIN jd_product jp ON pw.platform = 'JD' AND jp.id = pw.product_id " +
            "LEFT JOIN taobao_product tp ON pw.platform = 'TAOBAO' AND tp.id = pw.product_id " +
            "WHERE pw.user_id = #{userId} AND pw.status = 1 " +
            "ORDER BY pw.start_at DESC, pw.id DESC")
    List<PriceWatchListRow> findByUserId(@Param("userId") Long userId);

    @Select("SELECT platform, product_id AS productId FROM price_watch WHERE id = #{watchId} AND user_id = #{userId} LIMIT 1")
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
        public String getPlatform() { return platform; }
        public void setPlatform(String platform) { this.platform = platform; }
        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
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
        private LocalDateTime startAt;
        private LocalDateTime endAt;
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
        public LocalDateTime getStartAt() { return startAt; }
        public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }
        public LocalDateTime getEndAt() { return endAt; }
        public void setEndAt(LocalDateTime endAt) { this.endAt = endAt; }
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
        public Integer getProductStatus() { return productStatus; }
        public void setProductStatus(Integer productStatus) { this.productStatus = productStatus; }
    }
}
