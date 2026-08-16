package com.fishingtime.game.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 《细节》题库实体（PRD §14：图片不入库，只维护 image_key 对应的题目/选项/答案）
 */
@Data
public class DetailQuestion {

    private Long id;
    /** 图片唯一标识（pic_a/pic_b/...），对应 static/games/detail/{image_key}.png */
    private String imageKey;
    private String questionText;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    /** 正确答案 A/B/C/D */
    private String correctOption;
    /** 简单/中等/较难（V1 随机选图暂不使用） */
    private String difficulty;
    /** 1=启用 0=停用（抽题只取启用） */
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
