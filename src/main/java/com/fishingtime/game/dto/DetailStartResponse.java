package com.fishingtime.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 《细节》开局响应
 */
@Data
@AllArgsConstructor
public class DetailStartResponse {

    /** 本局会话 id（后续 draw/answer/finish 使用） */
    private String sessionId;
    /** 每轮观察时间（固定 10 秒，仅展示，不参与排名） */
    private int observationMs;
    /** 5 轮图片信息 */
    private List<DetailRoundInfo> rounds;
}
