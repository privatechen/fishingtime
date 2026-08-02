package com.fishingtime.banner.service;

import com.fishingtime.banner.dto.DailySentenceDTO;

/**
 * 每日一句服务接口
 */
public interface DailySentenceService {

    /**
     * 随机获取一条启用的句子
     * 数据库为空时返回默认文案
     */
    DailySentenceDTO getRandom();
}
