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
}
