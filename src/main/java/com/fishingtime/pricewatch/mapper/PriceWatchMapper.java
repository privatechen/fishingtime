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
            "VALUES (#{userId}, 'JD', #{productId}, #{purchasePrice}, #{startAt}, #{endAt}, 1)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PriceWatchInsertParam param);

    @Select("SELECT pw.id AS watchId, pw.platform AS platform, jp.sku_id AS skuId, " +
            "jp.product_url AS productUrl, jp.image_url AS imageUrl, pw.purchase_price AS purchasePrice, " +
            "pw.start_at AS startAt, pw.end_at AS endAt, pw.status AS status, " +
            "jp.status AS productStatus " +
            "FROM price_watch pw JOIN jd_product jp ON jp.id = pw.product_id " +
            "WHERE pw.user_id = #{userId} AND pw.status = 1 " +
            "ORDER BY pw.start_at DESC, pw.id DESC")
    List<PriceWatchListRow> findByUserId(@Param("userId") Long userId);

    @Select("SELECT product_id FROM price_watch WHERE id = #{watchId} AND user_id = #{userId} LIMIT 1")
    Long findProductIdByWatchAndUser(@Param("watchId") Long watchId, @Param("userId") Long userId);

    @Update("UPDATE price_watch SET status = 0 WHERE id = #{watchId} AND user_id = #{userId} AND status = 1")
    int disableByUser(@Param("watchId") Long watchId, @Param("userId") Long userId);

    class PriceWatchInsertParam {
        private Long id;
        private Long userId;
        private Long productId;
        private BigDecimal purchasePrice;
        private LocalDateTime startAt;
        private LocalDateTime endAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public BigDecimal getPurchasePrice() { return purchasePrice; }
        public void setPurchasePrice(BigDecimal purchasePrice) { this.purchasePrice = purchasePrice; }
        public LocalDateTime getStartAt() { return startAt; }
        public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }
        public LocalDateTime getEndAt() { return endAt; }
        public void setEndAt(LocalDateTime endAt) { this.endAt = endAt; }
    }

    class PriceWatchListRow {
        private Long watchId;
        private String platform;
        private String skuId;
        private String productUrl;
        private String imageUrl;
        private BigDecimal purchasePrice;
        private LocalDateTime startAt;
        private LocalDateTime endAt;
        private Integer status;
        private Integer productStatus;

        public Long getWatchId() { return watchId; }
        public void setWatchId(Long watchId) { this.watchId = watchId; }
        public String getPlatform() { return platform; }
        public void setPlatform(String platform) { this.platform = platform; }
        public String getSkuId() { return skuId; }
        public void setSkuId(String skuId) { this.skuId = skuId; }
        public String getProductUrl() { return productUrl; }
        public void setProductUrl(String productUrl) { this.productUrl = productUrl; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
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
