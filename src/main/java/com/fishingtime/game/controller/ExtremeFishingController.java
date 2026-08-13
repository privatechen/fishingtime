package com.fishingtime.game.controller;

import com.fishingtime.auth.CurrentUser;
import com.fishingtime.auth.CurrentUserInfo;
import com.fishingtime.common.dto.ApiResponse;
import com.fishingtime.common.dto.ErrorCode;
import com.fishingtime.game.domain.ExtremeFishingScore;
import com.fishingtime.game.dto.ExtremeFishingRankItemDTO;
import com.fishingtime.game.dto.ExtremeFishingScoreSubmitDTO;
import com.fishingtime.game.service.ExtremeFishingScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 极限捞鱼游戏 API
 *
 * GET  /api/games/extreme-fishing/rank      — 排行榜 Top20（公开，总分降序→河豚失误升序）
 * GET  /api/games/extreme-fishing/my-best   — 我的最佳成绩（需登录，未登录返回 null）
 * POST /api/games/extreme-fishing/score     — 提交本局成绩（需登录，仅在更优时更新）
 */
@Slf4j
@RestController
@RequestMapping("/api/games/extreme-fishing")
@RequiredArgsConstructor
public class ExtremeFishingController {

    private final ExtremeFishingScoreService scoreService;

    @GetMapping("/rank")
    public ApiResponse<List<ExtremeFishingRankItemDTO>> rank() {
        return ApiResponse.success(scoreService.getRank());
    }

    @GetMapping("/my-best")
    public ApiResponse<ExtremeFishingScore> myBest(@CurrentUser CurrentUserInfo currentUser) {
        if (currentUser == null) {
            return ApiResponse.success(null);
        }
        return ApiResponse.success(scoreService.getMyBest(currentUser.getUserId()));
    }

    @PostMapping("/score")
    public ApiResponse<Void> submit(@CurrentUser CurrentUserInfo currentUser,
                                    @RequestBody ExtremeFishingScoreSubmitDTO dto) {
        if (currentUser == null) {
            return ApiResponse.error(ErrorCode.UNAUTHORIZED);
        }
        scoreService.submitScore(currentUser.getUserId(), dto);
        return ApiResponse.success();
    }
}
