package com.fishingtime.game.controller;

import com.fishingtime.auth.CurrentUser;
import com.fishingtime.auth.CurrentUserInfo;
import com.fishingtime.common.dto.ApiResponse;
import com.fishingtime.common.dto.ErrorCode;
import com.fishingtime.game.domain.DetailScore;
import com.fishingtime.game.dto.DetailAnswerRequest;
import com.fishingtime.game.dto.DetailAnswerResponse;
import com.fishingtime.game.dto.DetailDrawRequest;
import com.fishingtime.game.dto.DetailDrawResponse;
import com.fishingtime.game.dto.DetailFinishResponse;
import com.fishingtime.game.dto.DetailStartResponse;
import com.fishingtime.game.mapper.DetailScoreMapper;
import com.fishingtime.game.service.DetailGameService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 《细节》游戏 API（服务端权威判定）
 *
 * POST /api/games/detail/start                        — 开局，返回 5 轮图片（不含答案）
 * POST /api/games/detail/{sessionId}/round/{round}/draw   — 盲选题号后返回问题+4选项（不含答案）
 * POST /api/games/detail/{sessionId}/round/{round}/answer — 提交答案，服务端判题与计时
 * POST /api/games/detail/{sessionId}/finish            — 结算（登录则落库，可重复调用补存）
 * GET  /api/games/detail/my-best                       — 我的最佳成绩（未登录返回 null）
 *
 * 排行榜走统一接口 GET /api/games/detail/leaderboard?period=TODAY|ALL
 */
@Slf4j
@RestController
@RequestMapping("/api/games/detail")
@RequiredArgsConstructor
public class DetailController {

    private final DetailGameService detailGameService;
    private final DetailScoreMapper detailScoreMapper;

    @PostMapping("/start")
    public ApiResponse<DetailStartResponse> start() {
        return ApiResponse.success(detailGameService.start());
    }

    @PostMapping("/{sessionId}/round/{round}/draw")
    public ApiResponse<DetailDrawResponse> draw(@PathVariable String sessionId,
                                                @PathVariable int round,
                                                @RequestBody DetailDrawRequest request) {
        if (request == null || request.getNumber() == null) {
            return ApiResponse.error(ErrorCode.PARAM_INVALID);
        }
        return ApiResponse.success(detailGameService.draw(sessionId, round, request.getNumber()));
    }

    @PostMapping("/{sessionId}/round/{round}/answer")
    public ApiResponse<DetailAnswerResponse> answer(@PathVariable String sessionId,
                                                    @PathVariable int round,
                                                    @RequestBody DetailAnswerRequest request) {
        String option = request != null ? request.getOption() : null;
        return ApiResponse.success(detailGameService.answer(sessionId, round, option));
    }

    @PostMapping("/{sessionId}/finish")
    public ApiResponse<DetailFinishResponse> finish(@PathVariable String sessionId,
                                                    @CurrentUser CurrentUserInfo currentUser) {
        Long userId = currentUser != null ? currentUser.getUserId() : null;
        return ApiResponse.success(detailGameService.finish(sessionId, userId));
    }

    @GetMapping("/my-best")
    public ApiResponse<DetailScore> myBest(@CurrentUser CurrentUserInfo currentUser) {
        if (currentUser == null) {
            return ApiResponse.success(null);
        }
        return ApiResponse.success(detailScoreMapper.selectByUserId(currentUser.getUserId()));
    }
}
