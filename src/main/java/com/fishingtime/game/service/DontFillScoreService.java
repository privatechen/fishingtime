package com.fishingtime.game.service;

import com.fishingtime.game.domain.DontFillScore;
import com.fishingtime.game.dto.DontFillScoreSubmitDTO;

public interface DontFillScoreService {
    DontFillScore getMyBest(Long userId);
    void submitScore(Long userId, DontFillScoreSubmitDTO dto);
}
