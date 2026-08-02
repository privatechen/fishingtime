package com.fishingtime.banner.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 每日一句实体
 */
@Data
public class DailySentence {

    private Long id;
    private String content;
    private String category;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
