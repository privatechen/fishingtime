package com.fishingtime.game.service;

import com.fishingtime.game.domain.ColorFocusScore;
import com.fishingtime.game.domain.ColorHunterScore;
import com.fishingtime.game.domain.DirectionTrapScore;
import com.fishingtime.game.domain.FishBreakoutScore;
import com.fishingtime.game.domain.Game2048Score;
import com.fishingtime.game.dto.GameRecordDTO;
import com.fishingtime.game.mapper.ColorFocusScoreMapper;
import com.fishingtime.game.mapper.ColorHunterScoreMapper;
import com.fishingtime.game.mapper.DirectionTrapScoreMapper;
import com.fishingtime.game.mapper.FishBreakoutScoreMapper;
import com.fishingtime.game.mapper.Game2048ScoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 我的游戏成绩聚合服务
 *
 * 一次返回四款游戏当前用户的核心最佳成绩（避免前端逐个游戏发起请求）。
 * 无记录的游戏返回字段为 null，由前端展示"暂无记录"。
 */
@Service
@RequiredArgsConstructor
public class MyGameRecordService {

    private final Game2048ScoreMapper game2048ScoreMapper;
    private final ColorFocusScoreMapper colorFocusScoreMapper;
    private final DirectionTrapScoreMapper directionTrapScoreMapper;
    private final ColorHunterScoreMapper colorHunterScoreMapper;
    private final FishBreakoutScoreMapper fishBreakoutScoreMapper;

    public List<GameRecordDTO> getMyRecords(Long userId) {
        List<GameRecordDTO> list = new ArrayList<>();
        list.add(build2048(userId));
        list.add(buildColorFocus(userId));
        list.add(buildDirectionTrap(userId));
        list.add(buildColorHunter(userId));
        list.add(buildFishBreakout(userId));
        return list;
    }

    private GameRecordDTO build2048(Long userId) {
        GameRecordDTO dto = new GameRecordDTO();
        dto.setGameType("2048");
        Game2048Score score = game2048ScoreMapper.selectByUserId(userId);
        if (score != null) {
            dto.setBestScore(score.getBestScore());
            dto.setMaxTile(score.getMaxTile());
        }
        return dto;
    }

    private GameRecordDTO buildColorFocus(Long userId) {
        GameRecordDTO dto = new GameRecordDTO();
        dto.setGameType("color-focus");
        ColorFocusScore score = colorFocusScoreMapper.selectByUserId(userId);
        if (score != null) {
            dto.setBestScore(score.getBestScore());
            dto.setBestAccuracy(score.getBestAccuracy());
            dto.setMaxStreak(score.getMaxStreak());
        }
        return dto;
    }

    private GameRecordDTO buildDirectionTrap(Long userId) {
        GameRecordDTO dto = new GameRecordDTO();
        dto.setGameType("direction-trap");
        DirectionTrapScore score = directionTrapScoreMapper.selectByUserId(userId);
        if (score != null) {
            dto.setBestScore(score.getBestScore());
            dto.setMaxStreak(score.getMaxStreak());
        }
        return dto;
    }

    private GameRecordDTO buildColorHunter(Long userId) {
        GameRecordDTO dto = new GameRecordDTO();
        dto.setGameType("color-hunter");
        ColorHunterScore score = colorHunterScoreMapper.selectByUserId(userId);
        if (score != null) {
            dto.setBestFinalTime(score.getBestFinalTime());
            dto.setBestActualTime(score.getBestActualTime());
            dto.setLowestErrorCount(score.getLowestErrorCount());
        }
        return dto;
    }

    private GameRecordDTO buildFishBreakout(Long userId) {
        GameRecordDTO dto = new GameRecordDTO();
        dto.setGameType("fish-breakout");
        FishBreakoutScore score = fishBreakoutScoreMapper.selectByUserId(userId);
        if (score != null) {
            dto.setBestClearedPools(score.getBestClearedPools());
            dto.setBestReleasedFish(score.getBestReleasedFish());
            dto.setBestMistakes(score.getBestMistakes());
            dto.setBestDuration(score.getBestDuration());
        }
        return dto;
    }
}
