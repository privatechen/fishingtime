package com.fishingtime.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 《细节》抽题响应：返回选中题号对应的题目与四个选项（不含正确答案）
 */
@Data
@AllArgsConstructor
public class DetailDrawResponse {

    /** 题目 id（供前端展示定位，不参与判题） */
    private Long questionId;
    private String questionText;
    /** 4 个选项文本（已随机乱序） */
    private String[] options;
    /** 与 options 对应的选项键 A/B/C/D（提交答案时回传该键） */
    private String[] optionKeys;
}
