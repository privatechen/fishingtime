package com.fishingtime.game.service;

import com.fishingtime.game.domain.*;
import com.fishingtime.game.dto.GameRecordDTO;
import com.fishingtime.game.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** 我的游戏成绩聚合服务。 */
@Service
@RequiredArgsConstructor
public class MyGameRecordService {

    private final Game2048ScoreMapper game2048ScoreMapper;
    private final ColorFocusScoreMapper colorFocusScoreMapper;
    private final DirectionTrapScoreMapper directionTrapScoreMapper;
    private final ColorHunterScoreMapper colorHunterScoreMapper;
    private final FishBreakoutScoreMapper fishBreakoutScoreMapper;
    private final ExtremeFishingScoreMapper extremeFishingScoreMapper;
    private final DontFillScoreMapper dontFillScoreMapper;
    private final StackTowerScoreMapper stackTowerScoreMapper;
    private final OneStrokeScoreMapper oneStrokeScoreMapper;

    public List<GameRecordDTO> getMyRecords(Long userId) {
        List<GameRecordDTO> list = new ArrayList<>();
        list.add(build2048(userId));
        list.add(buildColorFocus(userId));
        list.add(buildDirectionTrap(userId));
        list.add(buildColorHunter(userId));
        list.add(buildDontFill(userId));
        list.add(buildStackTower(userId));
        list.add(buildOneStroke(userId));
        list.add(buildFishBreakout(userId));
        list.add(buildExtremeFishing(userId));
        return list;
    }

    private GameRecordDTO build2048(Long userId) {
        GameRecordDTO dto = new GameRecordDTO(); dto.setGameType("2048");
        Game2048Score score = game2048ScoreMapper.selectByUserId(userId);
        if (score != null) { dto.setBestScore(score.getBestScore()); dto.setMaxTile(score.getMaxTile()); }
        return dto;
    }

    private GameRecordDTO buildColorFocus(Long userId) {
        GameRecordDTO dto = new GameRecordDTO(); dto.setGameType("color-focus");
        ColorFocusScore score = colorFocusScoreMapper.selectByUserId(userId);
        if (score != null) { dto.setBestScore(score.getBestScore()); dto.setBestAccuracy(score.getBestAccuracy()); dto.setMaxStreak(score.getMaxStreak()); }
        return dto;
    }

    private GameRecordDTO buildDirectionTrap(Long userId) {
        GameRecordDTO dto = new GameRecordDTO(); dto.setGameType("direction-trap");
        DirectionTrapScore score = directionTrapScoreMapper.selectByUserId(userId);
        if (score != null) { dto.setBestScore(score.getBestScore()); dto.setMaxStreak(score.getMaxStreak()); }
        return dto;
    }

    private GameRecordDTO buildColorHunter(Long userId) {
        GameRecordDTO dto = new GameRecordDTO(); dto.setGameType("color-hunter");
        ColorHunterScore score = colorHunterScoreMapper.selectByUserId(userId);
        if (score != null) { dto.setBestFinalTime(score.getBestFinalTime()); dto.setBestActualTime(score.getBestActualTime()); dto.setLowestErrorCount(score.getLowestErrorCount()); }
        return dto;
    }

    private GameRecordDTO buildDontFill(Long userId) {
        GameRecordDTO dto = new GameRecordDTO(); dto.setGameType("dont-fill");
        DontFillScore score = dontFillScoreMapper.selectByUserId(userId);
        if (score != null) { dto.setBestLevel(score.getLevel()); dto.setBestTime(score.getBestTime()); dto.setBestClearedLines(score.getClearedLines()); }
        return dto;
    }

    private GameRecordDTO buildStackTower(Long userId) {
        GameRecordDTO dto = new GameRecordDTO(); dto.setGameType("stack-tower");
        StackTowerScore score = stackTowerScoreMapper.selectByUserId(userId);
        if (score != null) { dto.setBestFloor(score.getMaxFloor()); dto.setStackPerfectCount(score.getPerfectCount()); }
        return dto;
    }

    private GameRecordDTO buildOneStroke(Long userId) {
        GameRecordDTO dto = new GameRecordDTO(); dto.setGameType("one-stroke");
        OneStrokeScore score = oneStrokeScoreMapper.selectByUserId(userId);
        if (score != null) dto.setOneStrokeMaxLevel(score.getMaxLevel());
        return dto;
    }

    private GameRecordDTO buildFishBreakout(Long userId) {
        GameRecordDTO dto = new GameRecordDTO(); dto.setGameType("fish-breakout");
        FishBreakoutScore score = fishBreakoutScoreMapper.selectByUserId(userId);
        if (score != null) { dto.setBestClearedPools(score.getBestClearedPools()); dto.setBestReleasedFish(score.getBestReleasedFish()); dto.setBestMistakes(score.getBestMistakes()); dto.setBestDuration(score.getBestDuration()); }
        return dto;
    }

    private GameRecordDTO buildExtremeFishing(Long userId) {
        GameRecordDTO dto = new GameRecordDTO(); dto.setGameType("extreme-fishing");
        ExtremeFishingScore score = extremeFishingScoreMapper.selectByUserId(userId);
        if (score != null) { dto.setBestScore(score.getBestScore()); dto.setBestPerfectCount(score.getBestPerfectCount()); dto.setBestMaxCombo(score.getBestMaxCombo()); dto.setBestPufferMistakes(score.getBestPufferMistakes()); }
        return dto;
    }
}
