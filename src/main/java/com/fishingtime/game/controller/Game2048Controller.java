package com.fishingtime.game.controller;

import com.fishingtime.auth.CurrentUser;
import com.fishingtime.auth.CurrentUserInfo;
import com.fishingtime.common.dto.ApiResponse;
import com.fishingtime.game.domain.Game2048Score;
import com.fishingtime.game.dto.RankItemDTO;
import com.fishingtime.game.dto.ScoreSubmitDTO;
import com.fishingtime.game.service.Game2048ScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 2048 游戏 API
 *
 * GET  /api/games/2048/rank      — 排行榜 Top20（公开）
 * GET  /api/games/2048/my-best   — 我的最高分（需登录）
 * POST /api/games/2048/score     — 提交最高分（需登录）
 */
@Slf4j
@RestController
@RequestMapping("/api/games/2048")
@RequiredArgsConstructor
public class Game2048Controller {

    private final Game2048ScoreService scoreService;

    @GetMapping("/rank")
    public ApiResponse<List<RankItemDTO>> rank() {
        return ApiResponse.success(scoreService.getRank());
    }

    @GetMapping("/my-best")
    public ApiResponse<Game2048Score> myBest(@CurrentUser CurrentUserInfo currentUser) {
        if (currentUser == null) {
            return ApiResponse.success(null);
        }
        return ApiResponse.success(scoreService.getMyBest(currentUser.getUserId()));
    }

    @PostMapping("/score")
    public ApiResponse<Void> submit(@CurrentUser CurrentUserInfo currentUser,
                                    @RequestBody ScoreSubmitDTO dto) {
        if (currentUser == null) {
            return ApiResponse.error(com.fishingtime.common.dto.ErrorCode.UNAUTHORIZED);
        }
        scoreService.submitScore(currentUser.getUserId(), currentUser.getNickname(), dto);
        return ApiResponse.success();
    }
}
