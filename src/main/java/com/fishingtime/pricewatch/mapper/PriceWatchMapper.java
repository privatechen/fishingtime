package com.fishingtime.pricewatch.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper
public interface PriceWatchMapper {

    @Insert("INSERT INTO price_watch (user_id, platform, product_id, purchase_price, start_at, end_at, status) " +
            "VALUES (#{userId}, 'JD', #{productId}, #{purchasePrice}, #{startAt}, #{endAt}, 1)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PriceWatchInsertParam param);

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
}
