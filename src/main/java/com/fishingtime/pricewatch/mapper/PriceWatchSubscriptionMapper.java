package com.fishingtime.pricewatch.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PriceWatchSubscriptionMapper {

    @Insert({
            "INSERT INTO price_watch_subscription ",
            "(user_id, watch_id, template_id, available_count, used_count, last_granted_at) ",
            "VALUES (#{userId}, #{watchId}, #{templateId}, 1, 0, NOW()) ",
            "ON DUPLICATE KEY UPDATE ",
            "available_count = available_count + 1, ",
            "last_granted_at = NOW()"
    })
    int grant(@Param("userId") Long userId,
              @Param("watchId") Long watchId,
              @Param("templateId") String templateId);

    @Update({
            "UPDATE price_watch_subscription ",
            "SET available_count = available_count - 1, ",
            "used_count = used_count + 1 ",
            "WHERE user_id = #{userId} ",
            "AND watch_id = #{watchId} ",
            "AND template_id = #{templateId} ",
            "AND available_count > 0"
    })
    int consume(@Param("userId") Long userId,
                @Param("watchId") Long watchId,
                @Param("templateId") String templateId);

    @Update({
            "UPDATE price_watch_subscription ",
            "SET available_count = 0 ",
            "WHERE user_id = #{userId} ",
            "AND watch_id = #{watchId} ",
            "AND template_id = #{templateId}"
    })
    int clearAvailable(@Param("userId") Long userId,
                       @Param("watchId") Long watchId,
                       @Param("templateId") String templateId);
}
