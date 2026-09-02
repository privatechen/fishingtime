package com.fishingtime.game.controller;

import com.fishingtime.auth.CurrentUser;
import com.fishingtime.auth.CurrentUserInfo;
import com.fishingtime.common.dto.ApiResponse;
import com.fishingtime.common.dto.ErrorCode;
import com.fishingtime.game.domain.OneStrokeScore;
import com.fishingtime.game.dto.OneStrokeScoreSubmitDTO;
import com.fishingtime.game.service.OneStrokeScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/games/one-stroke")
@RequiredArgsConstructor
public class OneStrokeController {

    private final OneStrokeScoreService scoreService;

    @GetMapping("/my-best")
    public ApiResponse<OneStrokeScore> myBest(@CurrentUser CurrentUserInfo currentUser) {
        if (currentUser == null) return ApiResponse.success(null);
        return ApiResponse.success(scoreService.getMyBest(currentUser.getUserId()));
    }

    @PostMapping("/score")
    public ApiResponse<Void> submit(@CurrentUser CurrentUserInfo currentUser,
                                    @RequestBody OneStrokeScoreSubmitDTO dto) {
        if (currentUser == null) return ApiResponse.error(ErrorCode.UNAUTHORIZED);
        scoreService.submitScore(currentUser.getUserId(), dto);
        return ApiResponse.success();
    }
}
