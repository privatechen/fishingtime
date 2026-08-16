package com.fishingtime.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 《细节》结算响应
 */
@Data
@AllArgsConstructor
public class DetailFinishResponse {

    /** 答对题数（0~5） */
    private int correctCount;
    /** 实际作答（含超时）的轮次数；未抽题的轮次不计入 */
    private int answeredCount;
    /** 累计答题用时（毫秒，仅统计题目展示到首次提交答案的耗时；超时按 8000 计） */
    private int answerTimeMs;
    /** 成绩是否已保存（未登录保存 false，登录后再调 finish 可补存） */
    private boolean saved;
    /** 历史最佳答对题数（未登录/无记录为 null） */
    private Integer bestCorrectCount;
    /** 历史最佳累计用时（毫秒） */
    private Integer bestAnswerTimeMs;
    /** 今日排名（未登录/暂无成绩为 null） */
    private Integer todayRank;
    /** 总排名（未登录/暂无成绩为 null） */
    private Integer allRank;
    /** 每轮明细（供前端展示计分过程） */
    private List<DetailRoundResult> rounds;
}
