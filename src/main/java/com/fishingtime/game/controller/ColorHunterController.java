package com.fishingtime.game.controller;

import com.fishingtime.auth.CurrentUser;
import com.fishingtime.auth.CurrentUserInfo;
import com.fishingtime.common.dto.ApiResponse;
import com.fishingtime.common.dto.ErrorCode;
import com.fishingtime.game.domain.ColorHunterScore;
import com.fishingtime.game.dto.ColorHunterRankItemDTO;
import com.fishingtime.game.dto.ColorHunterScoreSubmitDTO;
import com.fishingtime.game.service.ColorHunterScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 颜色猎手游戏 API
 *
 * GET  /api/games/color-hunter/rank      — 排行榜 Top20（公开，按 best_final_time 升序）
 * GET  /api/games/color-hunter/my-best   — 我的最佳成绩（需登录，未登录返回 null）
 * POST /api/games/color-hunter/score     — 提交本局成绩（需登录，各字段取更小值）
 */
@Slf4j
@RestController
@RequestMapping("/api/games/color-hunter")
@RequiredArgsConstructor
public class ColorHunterController {

    private final ColorHunterScoreService scoreService;

    @GetMapping("/rank")
    public ApiResponse<List<ColorHunterRankItemDTO>> rank() {
        return ApiResponse.success(scoreService.getRank());
    }

    @GetMapping("/my-best")
    public ApiResponse<ColorHunterScore> myBest(@CurrentUser CurrentUserInfo currentUser) {
        if (currentUser == null) {
            return ApiResponse.success(null);
        }
        return ApiResponse.success(scoreService.getMyBest(currentUser.getUserId()));
    }

    @PostMapping("/score")
    public ApiResponse<Void> submit(@CurrentUser CurrentUserInfo currentUser,
                                    @RequestBody ColorHunterScoreSubmitDTO dto) {
        if (currentUser == null) {
            return ApiResponse.error(ErrorCode.UNAUTHORIZED);
        }
        scoreService.submitScore(currentUser.getUserId(), dto);
        return ApiResponse.success();
    }
}
