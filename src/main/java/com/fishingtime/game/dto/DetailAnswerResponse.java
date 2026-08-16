package com.fishingtime.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 《细节》作答响应
 */
@Data
@AllArgsConstructor
public class DetailAnswerResponse {

    /** 是否答对 */
    private boolean correct;
    /** 正确答案选项键（乱序后） */
    private String correctOption;
    /** 正确答案文本 */
    private String correctAnswer;
    /** 本题答题用时（毫秒，clamp 到 [0, 8000]；超时按上限 8000 计） */
    private long elapsedMs;
}
