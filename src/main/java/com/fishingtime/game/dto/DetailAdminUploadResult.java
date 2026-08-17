package com.fishingtime.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 《细节》管理后台上传结果
 */
@Data
@AllArgsConstructor
public class DetailAdminUploadResult {

    /** 图片标识（由上传文件名推导） */
    private String imageKey;
    /** updated=已存在并更新 / created=新增 */
    private String action;
    /** 落库题数 */
    private int questionCount;
}
