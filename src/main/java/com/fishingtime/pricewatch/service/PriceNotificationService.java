package com.fishingtime.pricewatch.service;

import com.fishingtime.pricewatch.dto.PriceNotificationResponse;
import com.fishingtime.pricewatch.mapper.PriceNotificationMapper;
import lombok.RequiredArgsConstructor;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceNotificationService {

    private final PriceNotificationMapper notificationMapper;
    private final LocalDateTime startedAt = LocalDateTime.now();

    @Value("${price-watch.notification-scan-delay-ms:30000}")
    private long scanDelayMs;

    @PostConstruct
    public void started() {
        log.info("[站内降价通知] 定时任务已启动 scanDelayMs={}, startedAt={}",
                scanDelayMs, startedAt);
    }

    @Scheduled(fixedDelayString = "${price-watch.notification-scan-delay-ms:30000}")
    public void scanPriceNotifications() {
        try {
            int belowPurchase = createBelowPurchasePriceNotifications();
            int belowPrevious = createBelowPreviousPriceNotifications();
            log.info("[站内降价通知] 扫描完成 belowPurchase={}, belowPrevious={}, startedAt={}",
                    belowPurchase, belowPrevious, startedAt);
        } catch (Exception e) {
            // Notification generation is a side path and must never interrupt
            // existing watch-list or price-history functions.
            log.error("[站内降价通知] 扫描失败，现有采价功能不受影响", e);
        }
    }

    /**
     * Rule 1: the collected price crosses below the user's purchase price.
     */
    public int createBelowPurchasePriceNotifications() {
        int inserted = notificationMapper.insertBelowPurchasePriceNotifications(startedAt);
        if (inserted > 0) {
            log.info("[站内降价通知] 新增低于购买价通知 count={}", inserted);
        }
        return inserted;
    }

    /**
     * Rule 2: the collected price is lower than the immediately preceding quote.
     */
    public int createBelowPreviousPriceNotifications() {
        int inserted = notificationMapper.insertBelowPreviousPriceNotifications(startedAt);
        if (inserted > 0) {
            log.info("[站内降价通知] 新增低于上次报价通知 count={}", inserted);
        }
        return inserted;
    }

    public List<PriceNotificationResponse> unread(Long userId) {
        return notificationMapper.findUnreadByUser(userId).stream()
                .map(row -> PriceNotificationResponse.builder()
                        .id(row.getId())
                        .watchId(row.getWatchId())
                        .type(row.getType())
                        .productTitle(row.getProductTitle())
                        .currentPrice(row.getCurrentPrice())
                        .comparisonPrice(row.getComparisonPrice())
                        .dropAmount(row.getDropAmount())
                        .createdAt(row.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    public void markRead(Long userId, Long notificationId) {
        if (userId == null || notificationId == null) return;
        notificationMapper.markRead(userId, notificationId);
    }
}
