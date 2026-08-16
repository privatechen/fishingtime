package com.fishingtime.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 《细节》结算时的单轮明细（服务端权威，供前端展示计分过程）
 */
@Data
@AllArgsConstructor
public class DetailRoundResult {

    /** 轮次 1~5 */
    private int round;
    /** 是否实际参与（未抽题的轮次 = false，不计入成绩） */
    private boolean played;
    /** 是否答对 */
    private boolean correct;
    /** 是否超时（未抽题轮次恒为 false） */
    private boolean timeout;
    /** 本题用时（毫秒；超时按 8000 计） */
    private long elapsedMs;
}
