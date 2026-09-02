package com.fishingtime.analytics.service;

import com.fishingtime.analytics.dto.MiniappAnalyticsDTO;
import com.fishingtime.analytics.mapper.MiniappVisitMapper;
import com.fishingtime.user.domain.User;
import com.fishingtime.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collections;

@Service
@RequiredArgsConstructor
public class MiniappAnalyticsService {

    private final MiniappVisitMapper visitMapper;
    private final UserMapper userMapper;

    public void recordVisit(Long userId) {
        User user = userMapper.selectById(userId);
        boolean newUser = user != null
                && user.getCreatedAt() != null
                && LocalDate.now().equals(user.getCreatedAt().toLocalDate());
        visitMapper.upsertVisit(userId, newUser);
    }

    public MiniappAnalyticsDTO overview(int days) {
        int safeDays = Math.max(1, Math.min(days, 30));
        MiniappAnalyticsDTO.DailyStat today = visitMapper.selectToday();

        MiniappAnalyticsDTO dto = new MiniappAnalyticsDTO();
        if (today != null) {
            dto.setTodayUv(today.getUv());
            dto.setTodayPv(today.getPv());
            dto.setTodayNewUsers(today.getNewUsers());
            dto.setAvgVisits(today.getUv() == 0 ? 0D : Math.round((today.getPv() * 100D / today.getUv())) / 100D);
        }
        dto.setYesterdayUv(visitMapper.selectYesterdayUv());
        var recent = visitMapper.selectRecent(safeDays - 1);
        dto.setDaily(recent == null ? Collections.emptyList() : recent);
        return dto;
    }
}
