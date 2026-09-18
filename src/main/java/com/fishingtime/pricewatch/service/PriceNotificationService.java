package com.fishingtime.pricewatch.service;

import com.fishingtime.pricewatch.dto.PriceNotificationResponse;
import com.fishingtime.pricewatch.mapper.PriceNotificationMapper;
import com.fishingtime.pricewatch.mapper.PriceWatchMapper;
import com.fishingtime.pricewatch.mapper.PriceWatchSubscriptionMapper;
import com.fishingtime.user.domain.User;
import com.fishingtime.user.mapper.UserMapper;
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
    private final PriceWatchMapper priceWatchMapper;
    private final PriceWatchSubscriptionMapper subscriptionMapper;
    private final UserMapper userMapper;
    private final WechatSubscribeMessageService wechatSubscribeMessageService;
    private final LocalDateTime startedAt = LocalDateTime.now();

    @Value("${price-watch.notification-scan-delay-ms:30000}")
    private long scanDelayMs;

    @Value("${price-watch.wechat-template-id:SH59Aabm4qEo0P2RwMN3cENfCdtxU4bLdG9UJew5ZE4}")
    private String wechatTemplateId;

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
            int wechatSent = sendWechatPriceNotifications();
            log.info("[站内降价通知] 扫描完成 belowPurchase={}, belowPrevious={}, wechatSent={}, startedAt={}",
                    belowPurchase, belowPrevious, wechatSent, startedAt);
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

    public void grantWechatSubscription(Long userId, Long watchId) {
        if (userId == null || watchId == null || watchId <= 0) return;
        PriceWatchMapper.PriceWatchTarget target = priceWatchMapper.findTargetByWatchAndUser(watchId, userId);
        if (target == null) {
            throw new IllegalArgumentException("监控记录不存在");
        }
        subscriptionMapper.grant(userId, watchId, wechatTemplateId);
        log.info("[微信降价通知] 用户授权一次订阅消息 userId={}, watchId={}, templateId={}",
                userId, watchId, wechatTemplateId);
    }

    public int sendWechatPriceNotifications() {
        int sent = 0;
        try {
            List<PriceNotificationMapper.NotificationRow> candidates =
                    notificationMapper.findWechatCandidates(wechatTemplateId, startedAt);
            for (PriceNotificationMapper.NotificationRow row : candidates) {
                try {
                    User user = userMapper.selectById(row.getUserId());
                    if (user == null) {
                        notificationMapper.markWechatFailed(row.getId(), "用户不存在");
                        continue;
                    }

                    WechatSubscribeMessageService.SendResult result =
                            wechatSubscribeMessageService.send(user, row, wechatTemplateId);
                    if (result.success()) {
                        notificationMapper.markWechatSent(row.getId());
                        subscriptionMapper.consume(row.getUserId(), row.getWatchId(), wechatTemplateId);
                        sent++;
                        log.info("[微信降价通知] 发送成功 userId={}, watchId={}, notificationId={}",
                                row.getUserId(), row.getWatchId(), row.getId());
                    } else {
                        String error = "errcode=" + result.errCode() + ", errmsg=" + result.errMsg();
                        notificationMapper.markWechatFailed(row.getId(), truncateError(error));
                        if (result.errCode() == 43101) {
                            subscriptionMapper.clearAvailable(row.getUserId(), row.getWatchId(), wechatTemplateId);
                        }
                        log.warn("[微信降价通知] 发送失败 userId={}, watchId={}, notificationId={}, {}",
                                row.getUserId(), row.getWatchId(), row.getId(), error);
                    }
                } catch (Exception e) {
                    // 网络或 access_token 获取异常时保留 PENDING，下一轮继续重试。
                    log.warn("[微信降价通知] 本轮发送异常，保留待发送状态 userId={}, watchId={}, notificationId={}, message={}",
                            row.getUserId(), row.getWatchId(), row.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            // 订阅消息是附加能力，数据库迁移未执行或微信接口异常都不能影响站内通知。
            log.error("[微信降价通知] 扫描失败，站内通知不受影响", e);
        }
        return sent;
    }

    private String truncateError(String value) {
        if (value == null) return null;
        return value.length() <= 240 ? value : value.substring(0, 240);
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
