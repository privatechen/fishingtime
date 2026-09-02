package com.fishingtime.analytics.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class MiniappAnalyticsDTO {
    private int todayUv;
    private int todayPv;
    private int todayNewUsers;
    private int yesterdayUv;
    private double avgVisits;
    private List<DailyStat> daily;

    @Data
    public static class DailyStat {
        private LocalDate date;
        private int uv;
        private int pv;
        private int newUsers;
    }
}
