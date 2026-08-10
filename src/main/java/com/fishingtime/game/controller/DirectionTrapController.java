package com.fishingtime.game.controller;

import com.fishingtime.auth.CurrentUser;
import com.fishingtime.auth.CurrentUserInfo;
import com.fishingtime.common.dto.ApiResponse;
import com.fishingtime.common.dto.ErrorCode;
import com.fishingtime.game.domain.DirectionTrapScore;
import com.fishingtime.game.dto.DirectionTrapScoreSubmitDTO;
import com.fishingtime.game.dto.RankItemDTO;
import com.fishingtime.game.service.DirectionTrapScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 方向陷阱游戏 API
 *
 * GET  /api/games/direction-trap/rank      — 排行榜 Top20（公开）
 * GET  /api/games/direction-trap/my-best   — 我的最佳成绩（需登录，未登录返回 null）
 * POST /api/games/direction-trap/score     — 提交本局成绩（需登录，各字段取更优）
 */
@Slf4j
@RestController
@RequestMapping("/api/games/direction-trap")
@RequiredArgsConstructor
public class DirectionTrapController {

    private final DirectionTrapScoreService scoreService;

    @GetMapping("/rank")
    public ApiResponse<List<RankItemDTO>> rank() {
        return ApiResponse.success(scoreService.getRank());
    }

    @GetMapping("/my-best")
    public ApiResponse<DirectionTrapScore> myBest(@CurrentUser CurrentUserInfo currentUser) {
        if (currentUser == null) {
            return ApiResponse.success(null);
        }
        return ApiResponse.success(scoreService.getMyBest(currentUser.getUserId()));
    }

    @PostMapping("/score")
    public ApiResponse<Void> submit(@CurrentUser CurrentUserInfo currentUser,
                                    @RequestBody DirectionTrapScoreSubmitDTO dto) {
        if (currentUser == null) {
            return ApiResponse.error(ErrorCode.UNAUTHORIZED);
        }
        scoreService.submitScore(currentUser.getUserId(), dto);
        return ApiResponse.success();
    }
}
