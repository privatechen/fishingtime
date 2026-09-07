package com.fishingtime.game.controller;

import com.fishingtime.auth.CurrentUser;
import com.fishingtime.auth.CurrentUserInfo;
import com.fishingtime.common.dto.ApiResponse;
import com.fishingtime.common.dto.ErrorCode;
import com.fishingtime.game.domain.MemoryScore;
import com.fishingtime.game.dto.MemoryScoreSubmitDTO;
import com.fishingtime.game.service.MemoryScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/games/memory")
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryScoreService scoreService;

    @GetMapping("/my-best")
    public ApiResponse<MemoryScore> myBest(@CurrentUser CurrentUserInfo currentUser) {
        if (currentUser == null) return ApiResponse.success(null);
        return ApiResponse.success(scoreService.getMyBest(currentUser.getUserId()));
    }

    @PostMapping("/score")
    public ApiResponse<Void> submit(@CurrentUser CurrentUserInfo currentUser,
                                    @RequestBody MemoryScoreSubmitDTO dto) {
        if (currentUser == null) return ApiResponse.error(ErrorCode.UNAUTHORIZED);
        scoreService.submitScore(currentUser.getUserId(), dto);
        return ApiResponse.success();
    }
}
