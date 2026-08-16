package com.fishingtime.hot;

import com.fishingtime.hot.service.HotService;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.TriggerContext;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * 限流平台（抖音热榜）独立调度
 *
 * 抖音热榜走有月度额度的 uapis.cn API，不参与常规 10 分钟刷新。
 * 按上次执行时间的小时动态决定下次间隔：凌晨 2-6 点 +30 分钟，其余时间 +15 分钟。
 */
@Component
public class HotQuotaScheduler implements SchedulingConfigurer {

    private final HotService hotService;

    public HotQuotaScheduler(HotService hotService) {
        this.hotService = hotService;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addTriggerTask(hotService::refreshQuotaLimited, this::nextExecutionTime);
    }

    private Date nextExecutionTime(TriggerContext ctx) {
        LocalDateTime base = ctx.lastScheduledExecutionTime() != null
                ? LocalDateTime.ofInstant(ctx.lastScheduledExecutionTime().toInstant(), ZoneId.systemDefault())
                : LocalDateTime.now();
        int hour = base.getHour();
        int minutes = (hour >= 2 && hour < 6) ? 30 : 15;
        return Date.from(base.plusMinutes(minutes).atZone(ZoneId.systemDefault()).toInstant());
    }
}
