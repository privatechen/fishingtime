package com.fishingtime.game.service;

import com.fishingtime.game.domain.*;
import com.fishingtime.game.dto.GameRecordDTO;
import com.fishingtime.game.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service @RequiredArgsConstructor
public class MyGameRecordService {
    private final Game2048ScoreMapper game2048ScoreMapper; private final ColorFocusScoreMapper colorFocusScoreMapper;
    private final DirectionTrapScoreMapper directionTrapScoreMapper; private final ColorHunterScoreMapper colorHunterScoreMapper;
    private final FishBreakoutScoreMapper fishBreakoutScoreMapper; private final ExtremeFishingScoreMapper extremeFishingScoreMapper;
    private final DontFillScoreMapper dontFillScoreMapper; private final StackTowerScoreMapper stackTowerScoreMapper;
    private final OneStrokeScoreMapper oneStrokeScoreMapper; private final NumberPuzzleScoreMapper numberPuzzleScoreMapper;

    public List<GameRecordDTO> getMyRecords(Long userId){List<GameRecordDTO> l=new ArrayList<>();l.add(build2048(userId));l.add(buildColorFocus(userId));l.add(buildDirectionTrap(userId));l.add(buildColorHunter(userId));l.add(buildDontFill(userId));l.add(buildStackTower(userId));l.add(buildOneStroke(userId));l.add(buildNumberPuzzle(userId));l.add(buildFishBreakout(userId));l.add(buildExtremeFishing(userId));return l;}
    private GameRecordDTO build2048(Long id){GameRecordDTO d=new GameRecordDTO();d.setGameType("2048");Game2048Score s=game2048ScoreMapper.selectByUserId(id);if(s!=null){d.setBestScore(s.getBestScore());d.setMaxTile(s.getMaxTile());}return d;}
    private GameRecordDTO buildColorFocus(Long id){GameRecordDTO d=new GameRecordDTO();d.setGameType("color-focus");ColorFocusScore s=colorFocusScoreMapper.selectByUserId(id);if(s!=null){d.setBestScore(s.getBestScore());d.setBestAccuracy(s.getBestAccuracy());d.setMaxStreak(s.getMaxStreak());}return d;}
    private GameRecordDTO buildDirectionTrap(Long id){GameRecordDTO d=new GameRecordDTO();d.setGameType("direction-trap");DirectionTrapScore s=directionTrapScoreMapper.selectByUserId(id);if(s!=null){d.setBestScore(s.getBestScore());d.setMaxStreak(s.getMaxStreak());}return d;}
    private GameRecordDTO buildColorHunter(Long id){GameRecordDTO d=new GameRecordDTO();d.setGameType("color-hunter");ColorHunterScore s=colorHunterScoreMapper.selectByUserId(id);if(s!=null){d.setBestFinalTime(s.getBestFinalTime());d.setBestActualTime(s.getBestActualTime());d.setLowestErrorCount(s.getLowestErrorCount());}return d;}
    private GameRecordDTO buildDontFill(Long id){GameRecordDTO d=new GameRecordDTO();d.setGameType("dont-fill");DontFillScore s=dontFillScoreMapper.selectByUserId(id);if(s!=null){d.setBestLevel(s.getLevel());d.setBestTime(s.getBestTime());d.setBestClearedLines(s.getClearedLines());}return d;}
    private GameRecordDTO buildStackTower(Long id){GameRecordDTO d=new GameRecordDTO();d.setGameType("stack-tower");StackTowerScore s=stackTowerScoreMapper.selectByUserId(id);if(s!=null){d.setBestFloor(s.getMaxFloor());d.setStackPerfectCount(s.getPerfectCount());}return d;}
    private GameRecordDTO buildOneStroke(Long id){GameRecordDTO d=new GameRecordDTO();d.setGameType("one-stroke");OneStrokeScore s=oneStrokeScoreMapper.selectByUserId(id);if(s!=null)d.setOneStrokeMaxLevel(s.getMaxLevel());return d;}
    private GameRecordDTO buildNumberPuzzle(Long id){GameRecordDTO d=new GameRecordDTO();d.setGameType("number-puzzle");NumberPuzzleScore s3=numberPuzzleScoreMapper.selectBest(id,3),s4=numberPuzzleScoreMapper.selectBest(id,4),s5=numberPuzzleScoreMapper.selectBest(id,5);if(s3!=null){d.setNumberPuzzle3Time(s3.getElapsedMs());d.setNumberPuzzle3Steps(s3.getSteps());}if(s4!=null){d.setNumberPuzzle4Time(s4.getElapsedMs());d.setNumberPuzzle4Steps(s4.getSteps());}if(s5!=null){d.setNumberPuzzle5Time(s5.getElapsedMs());d.setNumberPuzzle5Steps(s5.getSteps());}return d;}
    private GameRecordDTO buildFishBreakout(Long id){GameRecordDTO d=new GameRecordDTO();d.setGameType("fish-breakout");FishBreakoutScore s=fishBreakoutScoreMapper.selectByUserId(id);if(s!=null){d.setBestClearedPools(s.getBestClearedPools());d.setBestReleasedFish(s.getBestReleasedFish());d.setBestMistakes(s.getBestMistakes());d.setBestDuration(s.getBestDuration());}return d;}
    private GameRecordDTO buildExtremeFishing(Long id){GameRecordDTO d=new GameRecordDTO();d.setGameType("extreme-fishing");ExtremeFishingScore s=extremeFishingScoreMapper.selectByUserId(id);if(s!=null){d.setBestScore(s.getBestScore());d.setBestPerfectCount(s.getBestPerfectCount());d.setBestMaxCombo(s.getBestMaxCombo());d.setBestPufferMistakes(s.getBestPufferMistakes());}return d;}
}
