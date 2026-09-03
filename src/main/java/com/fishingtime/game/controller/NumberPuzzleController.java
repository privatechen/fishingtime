package com.fishingtime.game.controller;

import com.fishingtime.auth.CurrentUser;
import com.fishingtime.auth.CurrentUserInfo;
import com.fishingtime.common.dto.ApiResponse;
import com.fishingtime.common.dto.ErrorCode;
import com.fishingtime.game.domain.NumberPuzzleScore;
import com.fishingtime.game.dto.NumberPuzzleRankItemDTO;
import com.fishingtime.game.dto.NumberPuzzleScoreSubmitDTO;
import com.fishingtime.game.mapper.NumberPuzzleScoreMapper;
import com.fishingtime.game.service.GameScoreLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/games/number-puzzle")
@RequiredArgsConstructor
public class NumberPuzzleController {
    private final NumberPuzzleScoreMapper mapper;
    private final GameScoreLogService gameScoreLogService;

    @PostMapping("/score")
    public ApiResponse<Void> submit(@CurrentUser CurrentUserInfo currentUser, @RequestBody NumberPuzzleScoreSubmitDTO dto) {
        if (currentUser == null) return ApiResponse.error(ErrorCode.UNAUTHORIZED);
        if (dto == null || dto.getDifficulty() == null || (dto.getDifficulty()!=3 && dto.getDifficulty()!=4 && dto.getDifficulty()!=5)
                || dto.getElapsedMs() == null || dto.getElapsedMs() <= 0 || dto.getSteps() == null || dto.getSteps() <= 0) {
            return ApiResponse.success();
        }
        NumberPuzzleScore score = new NumberPuzzleScore();
        score.setUserId(currentUser.getUserId());
        score.setDifficulty(dto.getDifficulty());
        score.setElapsedMs(dto.getElapsedMs());
        score.setSteps(dto.getSteps());
        score.setHintCount(dto.getHintCount() == null ? 0 : Math.max(0, dto.getHintCount()));
        mapper.insert(score);

        // Unified log powers Today/My cross-game views. Encode difficulty in secondary score;
        // the dedicated table remains the source for difficulty-specific puzzle rankings.
        gameScoreLogService.record(currentUser.getUserId(), "number-puzzle", dto.getElapsedMs(), dto.getDifficulty());
        return ApiResponse.success();
    }

    @GetMapping("/my-best")
    public ApiResponse<NumberPuzzleScore> myBest(@CurrentUser CurrentUserInfo currentUser, @RequestParam Integer difficulty) {
        if (currentUser == null) return ApiResponse.success(null);
        return ApiResponse.success(mapper.selectBest(currentUser.getUserId(), difficulty));
    }

    @GetMapping("/leaderboard")
    public ApiResponse<List<NumberPuzzleRankItemDTO>> leaderboard(@RequestParam Integer difficulty,
            @RequestParam(defaultValue="ALL") String period,
            @RequestParam(defaultValue="1") Integer page,
            @RequestParam(defaultValue="20") Integer pageSize) {
        if (difficulty == null || (difficulty != 3 && difficulty != 4 && difficulty != 5)) return ApiResponse.success(List.of());
        int p=Math.max(1,page), size=Math.min(50,Math.max(1,pageSize));
        return ApiResponse.success(mapper.selectLeaderboard(difficulty,
                "TODAY".equalsIgnoreCase(period) ? LocalDate.now().atStartOfDay() : null,
                (p-1)*size,size));
    }
}
