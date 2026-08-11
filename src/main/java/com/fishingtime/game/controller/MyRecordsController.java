package com.fishingtime.game.controller;

import com.fishingtime.auth.CurrentUser;
import com.fishingtime.auth.CurrentUserInfo;
import com.fishingtime.common.dto.ApiResponse;
import com.fishingtime.common.dto.ErrorCode;
import com.fishingtime.game.dto.GameRecordDTO;
import com.fishingtime.game.service.MyGameRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 我的游戏成绩 API
 *
 * GET /api/games/my-records — 一次返回四款游戏当前用户最佳成绩（需登录，Session 或小程序 token）
 */
@Slf4j
@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class MyRecordsController {

    private final MyGameRecordService myGameRecordService;

    @GetMapping("/my-records")
    public ApiResponse<List<GameRecordDTO>> myRecords(@CurrentUser CurrentUserInfo currentUser) {
        if (currentUser == null) {
            return ApiResponse.error(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(myGameRecordService.getMyRecords(currentUser.getUserId()));
    }
}
