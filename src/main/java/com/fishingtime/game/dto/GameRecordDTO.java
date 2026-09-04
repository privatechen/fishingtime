package com.fishingtime.game.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class GameRecordDTO {
    private String gameType;
    private Integer bestScore; private Integer maxTile; private BigDecimal bestAccuracy; private Integer maxStreak;
    private Integer bestFinalTime; private Integer bestActualTime; private Integer lowestErrorCount;
    private Integer bestClearedPools; private Integer bestReleasedFish; private Integer bestMistakes; private Integer bestDuration;
    private Integer bestPerfectCount; private Integer bestMaxCombo; private Integer bestPufferMistakes;
    private Integer bestLevel; private Long bestTime; private Integer bestClearedLines;
    private Integer bestFloor; private Integer stackPerfectCount; private Integer oneStrokeMaxLevel;
    /** 数字华容道：分别保存三档最佳耗时（毫秒）与对应步数。 */
    private Integer numberPuzzle3Time; private Integer numberPuzzle3Steps;
    private Integer numberPuzzle4Time; private Integer numberPuzzle4Steps;
    private Integer numberPuzzle5Time; private Integer numberPuzzle5Steps;
}
