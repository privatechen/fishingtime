package com.fishingtime.banner.dto;

import lombok.Data;

/**
 * 每日一句 DTO
 */
@Data
public class DailySentenceDTO {

    /** 句子内容 */
    private String content;

    /** 句子分类 */
    private String category;
}
