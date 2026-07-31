package com.fishingtime.hot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 热榜条目 DTO — 统一数据模型，所有平台共用
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotItemDTO {

    /** 排名 */
    private Integer rank;

    /** 标题 */
    private String title;

    /** 原始热度（字符串，如 "100万热度"、"剧集 542463"） */
    private String hotScore;

    /** 统一热度值（0～10000），由 HotScoreParser 生成 */
    private Integer normalizedHotScore;

    /** 简介（如有） */
    private String summary;

    /** 详情链接 */
    private String url;

    /** 回复数（虎扑） */
    private Integer replyCount;

    /** 浏览数（虎扑） */
    private Integer viewCount;

    /** 作者（虎扑） */
    private String author;

    /** 发布时间（虎扑） */
    private String publishTime;
}
