package com.fishingtime.pricewatch.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PriceHistoryMapper {

    @Select("SELECT DATE(ph.checked_at) AS priceDate, ph.price AS price " +
            "FROM price_history ph " +
            "JOIN (" +
            "  SELECT DATE(checked_at) AS price_date, MAX(checked_at) AS last_checked_at " +
            "  FROM price_history " +
            "  WHERE platform = #{platform} AND product_id = #{productId} " +
            "    AND checked_at >= #{startAt} AND checked_at <= #{endAt} " +
            "    AND checked_at >= DATE_SUB(NOW(), INTERVAL #{days} DAY) " +
            "  GROUP BY DATE(checked_at)" +
            ") daily ON DATE(ph.checked_at) = daily.price_date AND ph.checked_at = daily.last_checked_at " +
            "WHERE ph.platform = #{platform} AND ph.product_id = #{productId} " +
            "  AND ph.checked_at >= #{startAt} AND ph.checked_at <= #{endAt} " +
            "ORDER BY ph.checked_at ASC")
    List<PriceHistoryPointRow> findDailyLastPrices(@Param("platform") String platform,
                                                   @Param("productId") Long productId,
                                                   @Param("startAt") LocalDateTime startAt,
                                                   @Param("endAt") LocalDateTime endAt,
                                                   @Param("days") int days);


    @Select({
            "SELECT ph.checked_at AS checkedAt, ph.price AS price ",
            "FROM price_watch pw ",
            "JOIN price_history ph ON ph.platform = pw.platform AND ph.product_id = pw.product_id ",
            "WHERE pw.id = #{watchId} AND pw.user_id = #{userId} ",
            "AND ph.checked_at >= pw.start_at AND ph.checked_at <= pw.end_at ",
            "AND ph.price < pw.purchase_price ",
            "AND (NOT EXISTS (",
            "  SELECT 1 FROM price_history previous ",
            "  WHERE previous.platform = ph.platform AND previous.product_id = ph.product_id ",
            "  AND previous.checked_at >= pw.start_at AND previous.checked_at <= pw.end_at ",
            "  AND (previous.checked_at < ph.checked_at OR (previous.checked_at = ph.checked_at AND previous.id < ph.id))",
            ") OR (",
            "  SELECT previous.price FROM price_history previous ",
            "  WHERE previous.platform = ph.platform AND previous.product_id = ph.product_id ",
            "  AND previous.checked_at >= pw.start_at AND previous.checked_at <= pw.end_at ",
            "  AND (previous.checked_at < ph.checked_at OR (previous.checked_at = ph.checked_at AND previous.id < ph.id)) ",
            "  ORDER BY previous.checked_at DESC, previous.id DESC LIMIT 1",
            ") >= pw.purchase_price) ",
            "ORDER BY ph.checked_at DESC, ph.id DESC ",
            "LIMIT #{limit}"
    })
    List<PriceLowEventRow> findLowPriceEvents(@Param("watchId") Long watchId,
                                              @Param("userId") Long userId,
                                              @Param("limit") int limit);

    class PriceHistoryPointRow {
        private LocalDate priceDate;
        private BigDecimal price;

        public LocalDate getPriceDate() { return priceDate; }
        public void setPriceDate(LocalDate priceDate) { this.priceDate = priceDate; }
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
    }
    class PriceLowEventRow {
        private LocalDateTime checkedAt;
        private BigDecimal price;

        public LocalDateTime getCheckedAt() { return checkedAt; }
        public void setCheckedAt(LocalDateTime checkedAt) { this.checkedAt = checkedAt; }
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
    }

}
