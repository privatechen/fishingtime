package com.fishingtime.game.controller;

import com.fishingtime.auth.CurrentUser;
import com.fishingtime.auth.CurrentUserInfo;
import com.fishingtime.common.dto.ApiResponse;
import com.fishingtime.common.dto.ErrorCode;
import com.fishingtime.game.domain.StackTowerScore;
import com.fishingtime.game.dto.StackTowerScoreSubmitDTO;
import com.fishingtime.game.service.StackTowerScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/games/stack-tower")
@RequiredArgsConstructor
public class StackTowerController {

    private final StackTowerScoreService scoreService;

    @GetMapping("/my-best")
    public ApiResponse<StackTowerScore> myBest(@CurrentUser CurrentUserInfo currentUser) {
        if (currentUser == null) return ApiResponse.success(null);
        return ApiResponse.success(scoreService.getMyBest(currentUser.getUserId()));
    }

    @PostMapping("/score")
    public ApiResponse<Void> submit(@CurrentUser CurrentUserInfo currentUser,
                                    @RequestBody StackTowerScoreSubmitDTO dto) {
        if (currentUser == null) return ApiResponse.error(ErrorCode.UNAUTHORIZED);
        scoreService.submitScore(currentUser.getUserId(), dto);
        return ApiResponse.success();
    }
}
