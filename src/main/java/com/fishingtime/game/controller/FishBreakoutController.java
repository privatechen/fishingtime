package com.fishingtime.game.controller;

import com.fishingtime.auth.CurrentUser;
import com.fishingtime.auth.CurrentUserInfo;
import com.fishingtime.common.dto.ApiResponse;
import com.fishingtime.common.dto.ErrorCode;
import com.fishingtime.game.domain.FishBreakoutScore;
import com.fishingtime.game.dto.FishBreakoutRankItemDTO;
import com.fishingtime.game.dto.FishBreakoutScoreSubmitDTO;
import com.fishingtime.game.service.FishBreakoutScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 鱼群突围游戏 API
 *
 * GET  /api/games/fish-breakout/rank      — 排行榜 Top20（公开，清空池数→放生数→昵称）
 * GET  /api/games/fish-breakout/my-best   — 我的最佳成绩（需登录，未登录返回 null）
 * POST /api/games/fish-breakout/score     — 提交本局成绩（需登录，仅在更优时更新）
 */
@Slf4j
@RestController
@RequestMapping("/api/games/fish-breakout")
@RequiredArgsConstructor
public class FishBreakoutController {

    private final FishBreakoutScoreService scoreService;

    @GetMapping("/rank")
    public ApiResponse<List<FishBreakoutRankItemDTO>> rank() {
        return ApiResponse.success(scoreService.getRank());
    }

    @GetMapping("/my-best")
    public ApiResponse<FishBreakoutScore> myBest(@CurrentUser CurrentUserInfo currentUser) {
        if (currentUser == null) {
            return ApiResponse.success(null);
        }
        return ApiResponse.success(scoreService.getMyBest(currentUser.getUserId()));
    }

    @PostMapping("/score")
    public ApiResponse<Void> submit(@CurrentUser CurrentUserInfo currentUser,
                                    @RequestBody FishBreakoutScoreSubmitDTO dto) {
        if (currentUser == null) {
            return ApiResponse.error(ErrorCode.UNAUTHORIZED);
        }
        scoreService.submitScore(currentUser.getUserId(), dto);
        return ApiResponse.success();
    }
}
