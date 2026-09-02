package com.fishingtime.analytics.mapper;

import com.fishingtime.analytics.dto.MiniappAnalyticsDTO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MiniappVisitMapper {

    @Insert("INSERT INTO miniapp_daily_visit " +
            "(user_id, visit_date, visit_count, is_new_user, first_visit_time, last_visit_time, created_at, updated_at) " +
            "VALUES (#{userId}, CURDATE(), 1, #{newUser}, NOW(), NOW(), NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE " +
            "visit_count = visit_count + 1, " +
            "is_new_user = GREATEST(is_new_user, VALUES(is_new_user)), " +
            "last_visit_time = NOW(), updated_at = NOW()")
    int upsertVisit(@Param("userId") Long userId, @Param("newUser") boolean newUser);

    @Select("SELECT visit_date AS date, COUNT(*) AS uv, COALESCE(SUM(visit_count),0) AS pv, " +
            "COALESCE(SUM(is_new_user),0) AS newUsers " +
            "FROM miniapp_daily_visit WHERE visit_date = CURDATE() GROUP BY visit_date")
    MiniappAnalyticsDTO.DailyStat selectToday();

    @Select("SELECT COUNT(*) FROM miniapp_daily_visit WHERE visit_date = DATE_SUB(CURDATE(), INTERVAL 1 DAY)")
    int selectYesterdayUv();

    @Select("SELECT visit_date AS date, COUNT(*) AS uv, COALESCE(SUM(visit_count),0) AS pv, " +
            "COALESCE(SUM(is_new_user),0) AS newUsers " +
            "FROM miniapp_daily_visit " +
            "WHERE visit_date >= DATE_SUB(CURDATE(), INTERVAL #{daysMinusOne} DAY) " +
            "GROUP BY visit_date ORDER BY visit_date")
    List<MiniappAnalyticsDTO.DailyStat> selectRecent(@Param("daysMinusOne") int daysMinusOne);
}
