package com.fishingtime.game.controller;

import com.fishingtime.auth.CurrentUser;
import com.fishingtime.auth.CurrentUserInfo;
import com.fishingtime.common.dto.ApiResponse;
import com.fishingtime.common.dto.ErrorCode;
import com.fishingtime.game.domain.ColorFocusScore;
import com.fishingtime.game.dto.ColorFocusScoreSubmitDTO;
import com.fishingtime.game.dto.RankItemDTO;
import com.fishingtime.game.service.ColorFocusScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 选颜色游戏 API
 *
 * GET  /api/games/color-focus/rank      — 排行榜 Top20（公开）
 * GET  /api/games/color-focus/my-best   — 我的最佳成绩（需登录，未登录返回 null）
 * POST /api/games/color-focus/score     — 提交本局成绩（需登录，各字段取更优）
 */
@Slf4j
@RestController
@RequestMapping("/api/games/color-focus")
@RequiredArgsConstructor
public class ColorFocusController {

    private final ColorFocusScoreService scoreService;

    @GetMapping("/rank")
    public ApiResponse<List<RankItemDTO>> rank() {
        return ApiResponse.success(scoreService.getRank());
    }

    @GetMapping("/my-best")
    public ApiResponse<ColorFocusScore> myBest(@CurrentUser CurrentUserInfo currentUser) {
        if (currentUser == null) {
            return ApiResponse.success(null);
        }
        return ApiResponse.success(scoreService.getMyBest(currentUser.getUserId()));
    }

    @PostMapping("/score")
    public ApiResponse<Void> submit(@CurrentUser CurrentUserInfo currentUser,
                                    @RequestBody ColorFocusScoreSubmitDTO dto) {
        if (currentUser == null) {
            return ApiResponse.error(ErrorCode.UNAUTHORIZED);
        }
        scoreService.submitScore(currentUser.getUserId(), dto);
        return ApiResponse.success();
    }
}
