package com.fishingtime.game.service;

import com.fishingtime.game.domain.StackTowerScore;
import com.fishingtime.game.dto.StackTowerScoreSubmitDTO;

public interface StackTowerScoreService {
    StackTowerScore getMyBest(Long userId);
    void submitScore(Long userId, StackTowerScoreSubmitDTO dto);
}
