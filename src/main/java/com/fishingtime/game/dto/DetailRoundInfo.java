package com.fishingtime.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 《细节》单轮图片信息（start 返回）
 */
@Data
@AllArgsConstructor
public class DetailRoundInfo {

    /** 轮次 1~5 */
    private Integer round;
    /** 图片唯一标识 */
    private String imageKey;
    /** 图片访问地址 */
    private String imageUrl;
}
