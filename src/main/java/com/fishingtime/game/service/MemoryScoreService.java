package com.fishingtime.game.service;

import com.fishingtime.game.domain.MemoryScore;
import com.fishingtime.game.dto.MemoryScoreSubmitDTO;

public interface MemoryScoreService {
    MemoryScore getMyBest(Long userId);
    void submitScore(Long userId, MemoryScoreSubmitDTO dto);
}
