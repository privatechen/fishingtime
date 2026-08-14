package com.fishingtime.game.leaderboard;

import com.fishingtime.auth.CurrentUser;
import com.fishingtime.auth.CurrentUserInfo;
import com.fishingtime.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 小游戏排行榜统一接口
 *
 * GET /api/games/{gameCode}/leaderboard?period=TODAY|ALL&page=1&pageSize=20
 * - 公开（无需登录可查看）；登录后返回 myRank
 * - TODAY 今日榜 / ALL 总榜，均从 game_score 聚合，本地内存缓存 Cache First
 */
@RestController
@RequestMapping("/api/games/{gameCode}/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    @GetMapping
    public ApiResponse<LeaderboardDTO> leaderboard(@PathVariable String gameCode,
                                                   @RequestParam(defaultValue = "TODAY") String period,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int pageSize,
                                                   @CurrentUser CurrentUserInfo currentUser) {
        Long userId = currentUser != null ? currentUser.getUserId() : null;
        return ApiResponse.success(leaderboardService.getLeaderboard(gameCode, period, page, pageSize, userId));
    }
}
