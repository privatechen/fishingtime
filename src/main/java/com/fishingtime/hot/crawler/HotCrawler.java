package com.fishingtime.hot.crawler;

import com.fishingtime.hot.dto.HotItemDTO;

import java.util.List;

/**
 * 热榜抓取器接口 — 所有平台实现此接口
 *
 * 新增平台只需新建类实现此接口：
 * 1. platform() 返回唯一标识（如 "zhihu"）
 * 2. fetch() 实现抓取逻辑
 * 3. 自动被 HotService 纳入缓存和定时刷新
 */
public interface HotCrawler {

    /** 平台标识 — 同时也是 URL 路径名和缓存 key */
    String platform();

    /** 抓取并解析热点列表 */
    List<HotItemDTO> fetch();

    /**
     * 是否为限流平台（如抖音热榜走有月度额度的 API）。
     * true 时不会被常规 10 分钟定时刷新触发，改由独立限流调度器按时间窗口刷新。
     */
    default boolean quotaLimited() {
        return false;
    }
}
