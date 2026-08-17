package com.fishingtime.game.dto;

import com.fishingtime.game.domain.DetailQuestion;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 《细节》管理后台：单张图片 + 其题目（列表/编辑用）
 */
@Data
@AllArgsConstructor
public class DetailAdminImageVO {

    /** 图片标识 */
    private String imageKey;
    /** 题目数 */
    private int questionCount;
    /** 该图全部题目（含选项/答案） */
    private List<DetailQuestion> questions;
}
