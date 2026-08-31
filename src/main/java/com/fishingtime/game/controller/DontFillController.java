package com.fishingtime.game.controller;

import com.fishingtime.auth.CurrentUser;
import com.fishingtime.auth.CurrentUserInfo;
import com.fishingtime.common.dto.ApiResponse;
import com.fishingtime.common.dto.ErrorCode;
import com.fishingtime.game.domain.DontFillScore;
import com.fishingtime.game.dto.DontFillScoreSubmitDTO;
import com.fishingtime.game.service.DontFillScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 别堆满方块游戏 API
 *
 * GET  /api/games/dont-fill/my-best   — 我的最佳成绩
 * POST /api/games/dont-fill/score     — 提交本局成绩
 * 排行榜统一走 GET /api/games/dont-fill/leaderboard
 */
@RestController
@RequestMapping("/api/games/dont-fill")
@RequiredArgsConstructor
public class DontFillController {

    private final DontFillScoreService scoreService;

    @GetMapping("/my-best")
    public ApiResponse<DontFillScore> myBest(@CurrentUser CurrentUserInfo currentUser) {
        if (currentUser == null) {
            return ApiResponse.success(null);
        }
        return ApiResponse.success(scoreService.getMyBest(currentUser.getUserId()));
    }

    @PostMapping("/score")
    public ApiResponse<Void> submit(@CurrentUser CurrentUserInfo currentUser,
                                    @RequestBody DontFillScoreSubmitDTO dto) {
        if (currentUser == null) {
            return ApiResponse.error(ErrorCode.UNAUTHORIZED);
        }
        scoreService.submitScore(currentUser.getUserId(), dto);
        return ApiResponse.success();
    }
}
