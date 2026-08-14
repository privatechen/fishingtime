package com.fishingtime.game.leaderboard;

import lombok.Data;

import java.util.List;

/**
 * 排行榜响应 DTO
 */
@Data
public class LeaderboardDTO {

    /** TODAY / ALL */
    private String period;
    private String timezone;
    private List<Item> items;
    /** 当前用户排名；未登录或暂无成绩为 null */
    private MyRank myRank;
    /** 榜单参与人数（分页 total） */
    private int total;

    @Data
    public static class Item {
        private Integer rank;
        private String nickname;
        /** 主成绩（原始值；显示由前端按游戏格式化，或直接用 score） */
        private Integer score;
        /** 次级指标（鱼群突围=放生数；其余 null） */
        private Integer secondaryScore;
        /** 是否当前用户（高亮） */
        private boolean me;
    }

    @Data
    public static class MyRank {
        private Integer rank;
        private Integer score;
        private Integer secondaryScore;
    }
}
